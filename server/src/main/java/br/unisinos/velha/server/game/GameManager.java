package br.unisinos.velha.server.game;

import br.unisinos.velha.server.ServerConfig;
import br.unisinos.velha.server.db.GameRepository;
import br.unisinos.velha.server.events.EventLogger;
import br.unisinos.velha.server.net.ConnectionHandler;
import br.unisinos.velha.server.presence.PresenceService;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Cria e localiza partidas. Não há limite fixo de partidas simultâneas
 * (requisito 10): cada uma é uma {@link GameSession} com sua própria fila e
 * virtual thread consumidora.
 */
public class GameManager {

    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final Map<String, GameSession> byUser = new ConcurrentHashMap<>();
    private final GameRepository repo;
    private final EventLogger events;
    private final PresenceService presence;
    private final ScheduledExecutorService scheduler;
    private final ServerConfig cfg;

    public GameManager(GameRepository repo, EventLogger events, PresenceService presence,
                       ScheduledExecutorService scheduler, ServerConfig cfg) {
        this.repo = repo;
        this.events = events;
        this.presence = presence;
        this.scheduler = scheduler;
        this.cfg = cfg;
    }

    /**
     * Inicia uma partida entre quem convidou (X, começa) e quem aceitou (O).
     * Devolve {@code null} se algum dos dois já estiver em partida.
     */
    public synchronized GameSession start(ConnectionHandler x, ConnectionHandler o) {
        if (byUser.containsKey(key(x.username())) || byUser.containsKey(key(o.username()))) {
            return null;
        }
        int dbId = repo.create(x.userId(), o.userId());
        GameSession s = new GameSession(dbId, x, o, this, repo, events, scheduler, cfg.reconnectTimeout);
        games.put(s.gameId(), s);
        byUser.put(key(x.username()), s);
        byUser.put(key(o.username()), s);
        s.start();
        presence.broadcast(); // os dois aparecem como "em partida"
        return s;
    }

    public GameSession find(String gameId) {
        return gameId == null ? null : games.get(gameId);
    }

    public GameSession activeGameOf(String username) {
        return username == null ? null : byUser.get(key(username));
    }

    public int activeCount() {
        return games.size();
    }

    /** Chamado pela própria sessão quando termina. */
    synchronized void finished(GameSession s) {
        games.remove(s.gameId());
        byUser.remove(key(s.playerX().username), s);
        byUser.remove(key(s.playerO().username), s);
        presence.broadcast();
    }

    public void shutdown() {
        games.values().forEach(GameSession::shutdown);
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
