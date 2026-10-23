package br.unisinos.velha.server;

import br.unisinos.velha.common.tls.TlsConfig;
import br.unisinos.velha.server.auth.AuthService;
import br.unisinos.velha.server.auth.LoginRateLimiter;
import br.unisinos.velha.server.auth.PasswordHasher;
import br.unisinos.velha.server.db.Database;
import br.unisinos.velha.server.db.EventRepository;
import br.unisinos.velha.server.db.GameRepository;
import br.unisinos.velha.server.db.UserRepository;
import br.unisinos.velha.server.events.EventLogger;
import br.unisinos.velha.server.game.GameManager;
import br.unisinos.velha.server.lobby.LobbyService;
import br.unisinos.velha.server.net.ConnectionHandler;
import br.unisinos.velha.server.net.MessageRouter;
import br.unisinos.velha.server.presence.PresenceService;

import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monta os serviços e aceita conexões. Cada conexão roda numa virtual thread;
 * não há limite fixo de clientes nem de partidas (requisito 10).
 */
public class GameServer implements AutoCloseable {

    private final ServerConfig cfg;
    private final Database db;
    private final EventLogger events;
    private final GameRepository gameRepo;
    private final EventRepository eventRepo;
    private final PresenceService presence;
    private final GameManager games;
    private final MessageRouter router;
    private final ScheduledExecutorService scheduler;
    private final Set<ConnectionHandler> connections = ConcurrentHashMap.newKeySet();
    private final AtomicLong connIds = new AtomicLong();
    private ServerSocket serverSocket;
    private volatile boolean closed;

    public GameServer(ServerConfig cfg) throws SQLException {
        this.cfg = cfg;
        this.db = Database.open(cfg.dbPath);
        UserRepository users = new UserRepository(db);
        this.gameRepo = new GameRepository(db);
        this.eventRepo = new EventRepository(db);
        this.events = new EventLogger(eventRepo);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scheduler");
            t.setDaemon(true);
            return t;
        });
        this.presence = new PresenceService();
        this.games = new GameManager(gameRepo, events, presence, scheduler, cfg);
        presence.setInGameCheck(u -> games.activeGameOf(u) != null);
        LobbyService lobby = new LobbyService(presence, games, events, scheduler, cfg.inviteTimeout);
        AuthService auth = new AuthService(users, new PasswordHasher(cfg.pbkdf2Iterations),
                new LoginRateLimiter(cfg.loginMaxAttempts, cfg.loginWindow, Clock.systemUTC()), events);
        this.router = new MessageRouter(auth, presence, lobby, games, gameRepo, events, cfg);
    }

    public void start() throws IOException {
        int aborted = gameRepo.abortUnfinished();
        if (cfg.tls != null) {
            SSLServerSocket s = (SSLServerSocket) cfg.tls.getServerSocketFactory().createServerSocket(cfg.port);
            TlsConfig.restrict(s);
            serverSocket = s;
        } else {
            serverSocket = new ServerSocket(cfg.port);
        }
        events.info("SERVER_STARTED", null, null, "port", port(), "tls", cfg.tls != null,
                "abortedGames", aborted);
        Thread.ofPlatform().name("accept").daemon(false).start(this::acceptLoop);
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = serverSocket.accept();
                ConnectionHandler conn = new ConnectionHandler(connIds.incrementAndGet(), s, router, cfg, events);
                connections.add(conn);
                Thread.ofVirtual().name("conn-" + conn.id()).start(() -> {
                    try {
                        conn.run();
                    } finally {
                        connections.remove(conn);
                    }
                });
            } catch (IOException e) {
                if (!closed) {
                    events.error("ERROR", null, null, "where", "accept", "error", e.getMessage());
                }
            }
        }
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public int connectionCount() {
        return connections.size();
    }

    public int onlineCount() {
        return presence.count();
    }

    public int activeGames() {
        return games.activeCount();
    }

    public GameRepository gameRepository() {
        return gameRepo;
    }

    public EventRepository eventRepository() {
        return eventRepo;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        events.info("SERVER_STOPPED", null, null, "connections", connections.size());
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // encerrando
        }
        games.shutdown();
        connections.forEach(ConnectionHandler::close);
        // dá tempo às threads de conexão registrarem a desconexão antes de fechar o banco
        long deadline = System.currentTimeMillis() + 2000;
        while (!connections.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
            Thread.yield();
        }
        scheduler.shutdownNow();
        db.close();
    }
}
