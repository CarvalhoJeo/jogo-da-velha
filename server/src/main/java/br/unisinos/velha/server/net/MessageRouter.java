package br.unisinos.velha.server.net;

import br.unisinos.velha.common.protocol.ErrorCode;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import br.unisinos.velha.server.ServerConfig;
import br.unisinos.velha.server.auth.AuthService;
import br.unisinos.velha.server.auth.AuthService.AuthUser;
import br.unisinos.velha.server.auth.AuthService.LoginResult;
import br.unisinos.velha.server.db.GameRepository;
import br.unisinos.velha.server.db.GameRepository.LeaderboardRow;
import br.unisinos.velha.server.events.EventLogger;
import br.unisinos.velha.server.game.GameManager;
import br.unisinos.velha.server.game.GameSession;
import br.unisinos.velha.server.lobby.LobbyService;
import br.unisinos.velha.server.presence.PresenceService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Encaminha cada mensagem ao serviço certo ou à fila da partida. Também é
 * aqui que o servidor exige autenticação e confere o token de cada pedido.
 */
public class MessageRouter {

    /** Resposta genérica: não revela se o usuário existe ou se a senha está errada. */
    static final String GENERIC_AUTH_FAIL = "Usuário ou senha inválidos";
    static final String GENERIC_REGISTER_FAIL = "Não foi possível concluir o cadastro";

    private final AuthService auth;
    private final PresenceService presence;
    private final LobbyService lobby;
    private final GameManager games;
    private final GameRepository gameRepo;
    private final EventLogger events;
    private final ServerConfig cfg;

    public MessageRouter(AuthService auth, PresenceService presence, LobbyService lobby, GameManager games,
                         GameRepository gameRepo, EventLogger events, ServerConfig cfg) {
        this.auth = auth;
        this.presence = presence;
        this.lobby = lobby;
        this.games = games;
        this.gameRepo = gameRepo;
        this.events = events;
        this.cfg = cfg;
    }

    public void route(ConnectionHandler conn, Message m) {
        switch (m.type()) {
            case MessageType.PING -> conn.send(new Message(MessageType.PONG, m.seq(), null, m.payload()));
            case MessageType.PONG -> { /* heartbeat: o simples recebimento já renova o timeout de leitura */ }
            case MessageType.REGISTER -> register(conn, m);
            case MessageType.LOGIN -> login(conn, m);
            case MessageType.RECONNECT -> reconnect(conn, m);
            case MessageType.LOGOUT, MessageType.INVITE, MessageType.INVITE_REPLY,
                 MessageType.MOVE, MessageType.RESIGN, MessageType.LEADERBOARD -> {
                if (conn.user() == null || !Objects.equals(conn.token(), m.token())) {
                    conn.send(Message.error(m.seq(), ErrorCode.NOT_AUTHENTICATED, "Faça login primeiro"));
                    return;
                }
                authenticated(conn, m);
            }
            default -> {
                events.warn("UNKNOWN_TYPE", conn.userId(), null, "type", m.type());
                conn.send(Message.error(m.seq(), ErrorCode.UNKNOWN_TYPE, "Tipo desconhecido: " + m.type()));
            }
        }
    }

    private void authenticated(ConnectionHandler conn, Message m) {
        switch (m.type()) {
            case MessageType.LOGOUT -> logout(conn);
            case MessageType.INVITE -> lobby.invite(conn, m.seq(), m.text("targetUser"));
            case MessageType.INVITE_REPLY -> lobby.reply(conn, m.seq(), m.text("inviteId"), m.bool("accept"));
            case MessageType.MOVE -> {
                int cell = m.integer("cell");
                GameSession g = gameFor(conn, m);
                if (g != null) {
                    g.move(conn, m.seq(), cell);
                }
            }
            case MessageType.RESIGN -> {
                GameSession g = gameFor(conn, m);
                if (g != null) {
                    g.resign(conn, m.seq());
                }
            }
            case MessageType.LEADERBOARD -> leaderboard(conn, m);
            default -> throw new IllegalStateException(m.type());
        }
    }

    private GameSession gameFor(ConnectionHandler conn, Message m) {
        GameSession g = games.activeGameOf(conn.username());
        String requested = m.optText("gameId");
        if (g == null || (requested != null && !requested.equals(g.gameId()))) {
            conn.send(Message.error(m.seq(), ErrorCode.GAME_NOT_FOUND, "Partida não encontrada"));
            return null;
        }
        return g;
    }

    private void register(ConnectionHandler conn, Message m) {
        String username = m.text("username");
        String password = m.text("password");
        Optional<AuthUser> user = auth.register(username, password);
        if (user.isPresent()) {
            conn.send(new Message(MessageType.REGISTER_OK, m.seq(), null, Json.object().put("username", username)));
        } else {
            events.warn("REGISTER_FAILED", null, null, "username", username, "ip", conn.remoteIp());
            conn.send(new Message(MessageType.AUTH_FAIL, m.seq(), null, Json.object().put("reason", GENERIC_REGISTER_FAIL)));
        }
    }

    private void login(ConnectionHandler conn, Message m) {
        if (conn.user() != null) {
            conn.send(Message.error(m.seq(), ErrorCode.BAD_REQUEST, "Já autenticado nesta conexão"));
            return;
        }
        LoginResult r = auth.login(m.text("username"), m.text("password"), conn.remoteIp());
        switch (r.status()) {
            case BLOCKED -> conn.send(new Message(MessageType.AUTH_FAIL, m.seq(), null,
                    Json.object().put("reason", "Muitas tentativas. Aguarde um minuto e tente de novo.").put("blocked", true)));
            case FAIL -> conn.send(new Message(MessageType.AUTH_FAIL, m.seq(), null,
                    Json.object().put("reason", GENERIC_AUTH_FAIL)));
            case OK -> establish(conn, m.seq(), r.user(), auth.issueToken(r.user()), false);
        }
    }

    private void reconnect(ConnectionHandler conn, Message m) {
        String token = m.token() != null ? m.token() : m.optText("token");
        Optional<AuthUser> user = conn.user() == null
                ? auth.resume(token, cfg.reconnectTimeout.toMillis())
                : Optional.empty();
        if (user.isEmpty()) {
            events.warn("RECONNECT_FAILED", null, null, "ip", conn.remoteIp());
            conn.send(Message.error(m.seq(), ErrorCode.RECONNECT_FAILED, "Sessão expirada; faça login novamente"));
            return;
        }
        events.info("RECONNECT", user.get().id(), m.optText("gameId"), "username", user.get().username(), "ip", conn.remoteIp());
        establish(conn, m.seq(), user.get(), token, true);
    }

    /** Autentica a conexão, substitui uma sessão anterior do mesmo usuário e retoma a partida, se houver. */
    private void establish(ConnectionHandler conn, long seq, AuthUser user, String token, boolean reconnected) {
        conn.authenticate(user, token);
        ConnectionHandler old = presence.add(conn);
        if (old != null && old != conn) {
            old.send(Message.error(0, ErrorCode.SESSION_REPLACED, "Sessão aberta em outra conexão"));
            old.close();
        }
        conn.send(new Message(MessageType.AUTH_OK, seq, null, Json.object()
                .put("token", token).put("username", user.username()).put("reconnected", reconnected)));
        presence.broadcast();
        GameSession g = games.activeGameOf(user.username());
        if (g != null) {
            g.attach(conn);
        }
    }

    private void logout(ConnectionHandler conn) {
        events.info("LOGOUT", conn.userId(), null, "username", conn.username());
        GameSession g = games.activeGameOf(conn.username());
        if (g != null) {
            g.resign(conn, 0); // sair no meio da partida concede a vitória
        }
        auth.revoke(conn.token());
        presence.remove(conn);
        lobby.cancelInvitesOf(conn.username());
        conn.deauthenticate();
        presence.broadcast();
    }

    private void leaderboard(ConnectionHandler conn, Message m) {
        ArrayNode rows = Json.array();
        for (LeaderboardRow r : gameRepo.leaderboard(20)) {
            rows.add(Json.object().put("username", r.username()).put("wins", r.wins())
                    .put("draws", r.draws()).put("losses", r.losses()).put("games", r.games()));
        }
        ObjectNode p = Json.object();
        p.set("rows", rows);
        conn.send(new Message(MessageType.LEADERBOARD_DATA, m.seq(), null, p));
    }

    /** Chamado pela thread da conexão quando ela termina, por qualquer motivo. */
    public void onDisconnect(ConnectionHandler conn) {
        AuthUser user = conn.user();
        if (user == null) {
            return;
        }
        events.info("DISCONNECT", user.id(), null, "username", user.username());
        if (!presence.remove(conn)) {
            return; // já substituída por uma conexão mais nova do mesmo usuário
        }
        lobby.cancelInvitesOf(user.username());
        GameSession g = games.activeGameOf(user.username());
        if (g != null) {
            auth.markDisconnected(conn.token()); // token vale para RECONNECT durante a pausa
            g.disconnected(conn);
        } else {
            auth.revoke(conn.token());
        }
        presence.broadcast();
    }
}
