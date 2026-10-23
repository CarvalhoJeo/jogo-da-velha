package br.unisinos.velha.server.game;

import br.unisinos.velha.common.game.Mark;
import br.unisinos.velha.common.game.TicTacToeEngine;
import br.unisinos.velha.common.game.TicTacToeEngine.MoveResult;
import br.unisinos.velha.common.protocol.ErrorCode;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import br.unisinos.velha.server.db.GameRepository;
import br.unisinos.velha.server.events.EventLogger;
import br.unisinos.velha.server.net.ConnectionHandler;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Uma partida. Todas as ações (jogadas, desistência, quedas, reconexões e
 * timeouts) entram numa {@link BlockingQueue} e são processadas em ordem por
 * um único consumidor, então o tabuleiro nunca sofre condição de corrida.
 *
 * Estados: IN_PROGRESS, PAUSED_DISCONNECTED e FINISHED (docs/05-protocolo.md).
 */
public class GameSession {

    public enum State { IN_PROGRESS, PAUSED_DISCONNECTED, FINISHED }

    /** Um lado da partida. A conexão muda quando o jogador reconecta. */
    static final class PlayerSlot {
        final int userId;
        final String username;
        final Mark mark;
        volatile ConnectionHandler conn;

        PlayerSlot(ConnectionHandler conn, Mark mark) {
            this.userId = conn.userId();
            this.username = conn.username();
            this.mark = mark;
            this.conn = conn;
        }

        void send(Message m) {
            ConnectionHandler c = conn;
            if (c != null) {
                c.send(m);
            }
        }
    }

    private sealed interface GameEvent {
        record Move(ConnectionHandler conn, long seq, int cell) implements GameEvent {}
        record Resign(ConnectionHandler conn, String username, long seq) implements GameEvent {}
        record Disconnected(ConnectionHandler conn) implements GameEvent {}
        record Attach(ConnectionHandler conn) implements GameEvent {}
        record Timeout(long epoch) implements GameEvent {}
        record Shutdown() implements GameEvent {}
    }

    private final int dbId;
    private final String gameId;
    private final PlayerSlot x;
    private final PlayerSlot o;
    private final GameManager manager;
    private final GameRepository repo;
    private final EventLogger events;
    private final ScheduledExecutorService scheduler;
    private final Duration reconnectTimeout;
    private final BlockingQueue<GameEvent> queue = new LinkedBlockingQueue<>();
    private final TicTacToeEngine engine = new TicTacToeEngine();

    // só o consumidor altera; volatile para leitura em testes e no lobby
    private volatile State state = State.IN_PROGRESS;
    private long timeoutEpoch;

    GameSession(int dbId, ConnectionHandler playerX, ConnectionHandler playerO, GameManager manager,
                GameRepository repo, EventLogger events, ScheduledExecutorService scheduler, Duration reconnectTimeout) {
        this.dbId = dbId;
        this.gameId = "g-" + dbId;
        this.x = new PlayerSlot(playerX, Mark.X);
        this.o = new PlayerSlot(playerO, Mark.O);
        this.manager = manager;
        this.repo = repo;
        this.events = events;
        this.scheduler = scheduler;
        this.reconnectTimeout = reconnectTimeout;
    }

    void start() {
        events.info("GAME_STARTED", x.userId, gameId, "x", x.username, "o", o.username);
        x.send(gameStart(x, false));
        o.send(gameStart(o, false));
        broadcastState();
        Thread.ofVirtual().name("game-" + gameId).start(this::consume);
        // se alguém caiu entre o aceite e a criação da partida, a queda não pode se perder
        for (PlayerSlot p : new PlayerSlot[]{x, o}) {
            ConnectionHandler c = p.conn;
            if (c != null && c.isClosed()) {
                disconnected(c);
            }
        }
    }

    // ---- API usada pelo MessageRouter (qualquer thread): só enfileira ----

    public void move(ConnectionHandler conn, long seq, int cell) {
        queue.add(new GameEvent.Move(conn, seq, cell));
    }

    public void resign(ConnectionHandler conn, long seq) {
        queue.add(new GameEvent.Resign(conn, conn.username(), seq));
    }

    public void disconnected(ConnectionHandler conn) {
        queue.add(new GameEvent.Disconnected(conn));
    }

    /** O jogador voltou numa conexão nova (RECONNECT ou novo LOGIN). */
    public void attach(ConnectionHandler conn) {
        queue.add(new GameEvent.Attach(conn));
    }

    void shutdown() {
        queue.add(new GameEvent.Shutdown());
    }

    // ---- consumidor ----

    private void consume() {
        try {
            while (state != State.FINISHED) {
                GameEvent e = queue.take();
                try {
                    handle(e);
                } catch (RuntimeException ex) {
                    events.error("ERROR", null, gameId, "event", e.getClass().getSimpleName(), "error", ex.toString());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(GameEvent e) {
        switch (e) {
            case GameEvent.Move m -> onMove(m);
            case GameEvent.Resign r -> onResign(r);
            case GameEvent.Disconnected d -> onDisconnected(d.conn());
            case GameEvent.Attach a -> onAttach(a.conn());
            case GameEvent.Timeout t -> onTimeout(t.epoch());
            case GameEvent.Shutdown s -> state = State.FINISHED;
        }
    }

    private PlayerSlot slotOf(ConnectionHandler conn) {
        return slotOf(conn, conn.username());
    }

    private PlayerSlot slotOf(ConnectionHandler conn, String u) {
        if (u == null) {
            return null;
        }
        PlayerSlot s = u.equalsIgnoreCase(x.username) ? x : u.equalsIgnoreCase(o.username) ? o : null;
        // só aceita ações da conexão atual do jogador
        return s != null && s.conn == conn ? s : null;
    }

    private void onMove(GameEvent.Move m) {
        PlayerSlot p = slotOf(m.conn());
        if (p == null) {
            m.conn().send(Message.error(m.seq(), ErrorCode.GAME_NOT_FOUND, "Você não está nesta partida"));
            return;
        }
        if (state == State.PAUSED_DISCONNECTED) {
            p.send(Message.error(m.seq(), ErrorCode.GAME_PAUSED, "Partida pausada: aguardando o oponente reconectar"));
            return;
        }
        MoveResult r = engine.play(p.mark, m.cell());
        switch (r) {
            case NOT_YOUR_TURN -> p.send(Message.error(m.seq(), ErrorCode.NOT_YOUR_TURN, "Não é a sua vez"));
            case OCCUPIED -> p.send(Message.error(m.seq(), ErrorCode.INVALID_MOVE, "Casa ocupada"));
            case OUT_OF_RANGE -> p.send(Message.error(m.seq(), ErrorCode.INVALID_MOVE, "Casa fora de 0 a 8"));
            case GAME_OVER -> p.send(Message.error(m.seq(), ErrorCode.INVALID_MOVE, "Partida encerrada"));
            case OK -> {
                repo.addMove(dbId, p.userId, m.cell(), engine.moveCount());
                events.info("MOVE", p.userId, gameId, "username", p.username, "cell", m.cell(), "n", engine.moveCount());
                broadcastState();
                if (engine.winner() != Mark.EMPTY) {
                    finish(p, "THREE_IN_ROW");
                } else if (engine.isDraw()) {
                    finish(null, "DRAW");
                }
            }
        }
    }

    private void onResign(GameEvent.Resign r) {
        PlayerSlot p = slotOf(r.conn(), r.username());
        if (p == null) {
            r.conn().send(Message.error(r.seq(), ErrorCode.GAME_NOT_FOUND, "Você não está nesta partida"));
            return;
        }
        events.info("RESIGN", p.userId, gameId, "username", p.username);
        finish(other(p), "RESIGN");
    }

    private void onDisconnected(ConnectionHandler conn) {
        PlayerSlot p = slotOf(conn);
        if (p == null) {
            return; // conexão antiga, já substituída
        }
        p.conn = null;
        if (state == State.IN_PROGRESS) {
            state = State.PAUSED_DISCONNECTED;
            long epoch = ++timeoutEpoch;
            scheduler.schedule(() -> queue.add(new GameEvent.Timeout(epoch)),
                    reconnectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            repo.updateStatus(dbId, State.PAUSED_DISCONNECTED.name());
            events.warn("GAME_PAUSED", p.userId, gameId, "disconnected", p.username,
                    "timeoutSeconds", reconnectTimeout.toSeconds());
        }
        other(p).send(Message.of(MessageType.OPPONENT_DISCONNECTED, Json.object()
                .put("gameId", gameId).put("username", p.username)
                .put("timeoutSeconds", reconnectTimeout.toSeconds())));
    }

    private void onAttach(ConnectionHandler conn) {
        String u = conn.username();
        PlayerSlot p = u == null ? null : u.equalsIgnoreCase(x.username) ? x : u.equalsIgnoreCase(o.username) ? o : null;
        if (p == null || state == State.FINISHED) {
            return;
        }
        p.conn = conn;
        p.send(gameStart(p, true));
        p.send(stateMessage());
        if (state == State.PAUSED_DISCONNECTED && x.conn != null && o.conn != null) {
            state = State.IN_PROGRESS;
            timeoutEpoch++; // invalida o timeout agendado
            repo.updateStatus(dbId, State.IN_PROGRESS.name());
            events.info("GAME_RESUMED", p.userId, gameId, "reconnected", p.username);
            other(p).send(Message.of(MessageType.OPPONENT_RECONNECTED,
                    Json.object().put("gameId", gameId).put("username", p.username)));
            broadcastState();
        } else if (state == State.PAUSED_DISCONNECTED) {
            // o oponente também caiu: avisa quem voltou
            p.send(Message.of(MessageType.OPPONENT_DISCONNECTED, Json.object()
                    .put("gameId", gameId).put("username", other(p).username)
                    .put("timeoutSeconds", reconnectTimeout.toSeconds())));
        }
    }

    private void onTimeout(long epoch) {
        if (epoch != timeoutEpoch || state != State.PAUSED_DISCONNECTED) {
            return;
        }
        boolean xGone = x.conn == null;
        boolean oGone = o.conn == null;
        PlayerSlot winner = xGone && oGone ? null : xGone ? o : x;
        finish(winner, "TIMEOUT");
    }

    /** {@code winner} nulo: empate (DRAW) ou abandono dos dois (TIMEOUT). */
    private void finish(PlayerSlot winner, String reason) {
        state = State.FINISHED;
        repo.finish(dbId, winner == null ? null : winner.userId, reason);
        events.info("GAME_ENDED", winner == null ? null : winner.userId, gameId,
                "x", x.username, "o", o.username, "winner", winner == null ? null : winner.username,
                "reason", reason, "moves", engine.moveCount());
        for (PlayerSlot p : new PlayerSlot[]{x, o}) {
            String result = winner == null ? "DRAW" : winner == p ? "WIN" : "LOSS";
            ObjectNode payload = Json.object()
                    .put("gameId", gameId)
                    .put("result", result)
                    .put("reason", reason)
                    .put("winner", winner == null ? null : winner.username);
            payload.set("board", boardJson());
            int[] line = engine.winningLine();
            if (line != null && "THREE_IN_ROW".equals(reason)) {
                ArrayNode l = payload.putArray("winningLine");
                for (int c : line) {
                    l.add(c);
                }
            }
            p.send(Message.of(MessageType.GAME_OVER, payload));
        }
        manager.finished(this);
    }

    // ---- mensagens ----

    private Message gameStart(PlayerSlot p, boolean resumed) {
        return Message.of(MessageType.GAME_START, Json.object()
                .put("gameId", gameId)
                .put("opponent", other(p).username)
                .put("symbol", p.mark.name())
                .put("firstTurn", Mark.X.name())
                .put("resumed", resumed));
    }

    private Message stateMessage() {
        ObjectNode p = Json.object()
                .put("gameId", gameId)
                .put("turn", engine.isOver() ? null : engine.turn().name())
                .put("lastMove", engine.lastMove())
                .put("moveCount", engine.moveCount())
                .put("status", state.name());
        p.set("board", boardJson());
        return Message.of(MessageType.GAME_STATE, p);
    }

    private void broadcastState() {
        Message m = stateMessage();
        x.send(m);
        o.send(m);
    }

    private ArrayNode boardJson() {
        ArrayNode a = Json.array();
        for (Mark m : engine.board()) {
            a.add(m.symbol());
        }
        return a;
    }

    private PlayerSlot other(PlayerSlot p) {
        return p == x ? o : x;
    }

    public String gameId() {
        return gameId;
    }

    public int dbId() {
        return dbId;
    }

    public State state() {
        return state;
    }

    PlayerSlot playerX() {
        return x;
    }

    PlayerSlot playerO() {
        return o;
    }
}
