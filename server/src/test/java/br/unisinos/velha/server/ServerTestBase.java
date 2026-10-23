package br.unisinos.velha.server;

import br.unisinos.velha.common.net.ProtocolClient;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Sobe um servidor real (TLS, SQLite temporário) por teste e oferece atalhos de protocolo. */
abstract class ServerTestBase {

    static final Duration WAIT = Duration.ofSeconds(5);
    static final String PASSWORD = "senha123";

    @TempDir
    Path tmp;
    ServerConfig cfg;
    GameServer server;
    final List<ProtocolClient> clients = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        cfg = baseConfig(tmp.resolve("test.db"));
        configure(cfg);
        server = new GameServer(cfg);
        server.start();
    }

    static ServerConfig baseConfig(Path db) throws Exception {
        ServerConfig c = new ServerConfig();
        c.port = 0;
        c.dbPath = db;
        c.tls = TestCerts.serverContext();
        c.pbkdf2Iterations = 1_000; // rápido nos testes; o padrão de produção é 210k
        c.inviteTimeout = Duration.ofMillis(800);
        c.reconnectTimeout = Duration.ofMillis(1500);
        return c;
    }

    /** Sobrescreva para ajustar a configuração de um teste. */
    void configure(ServerConfig c) {
    }

    @AfterEach
    void stopServer() {
        clients.forEach(ProtocolClient::close);
        server.close();
    }

    ProtocolClient connect() throws Exception {
        ProtocolClient c = ProtocolClient.connect("localhost", server.port(), TestCerts.clientContext());
        clients.add(c);
        return c;
    }

    /** Conecta, cadastra (se preciso) e faz login; o token fica guardado no cliente. */
    ProtocolClient login(String username) throws Exception {
        ProtocolClient c = connect();
        c.request(MessageType.REGISTER, Json.object().put("username", username).put("password", PASSWORD));
        c.await(m -> m.type().equals(MessageType.REGISTER_OK) || m.type().equals(MessageType.AUTH_FAIL), WAIT);
        c.request(MessageType.LOGIN, Json.object().put("username", username).put("password", PASSWORD));
        Message ok = expect(c, MessageType.AUTH_OK);
        c.setToken(ok.text("token"));
        return c;
    }

    static Message expect(ProtocolClient c, String type) throws InterruptedException {
        Message m = c.await(type, WAIT);
        assertNotNull(m, "esperava " + type);
        return m;
    }

    static Message expectError(ProtocolClient c, String code) throws InterruptedException {
        Message m = c.await(x -> x.type().equals(MessageType.ERROR) && code.equals(x.optText("code")), WAIT);
        assertNotNull(m, "esperava ERROR " + code);
        return m;
    }

    /** A convida B, B aceita. Devolve o gameId. A é X e começa. */
    static String startGame(ProtocolClient a, ProtocolClient b, String bName) throws Exception {
        a.request(MessageType.INVITE, Json.object().put("targetUser", bName));
        Message inv = expect(b, MessageType.INVITE_RECEIVED);
        b.request(MessageType.INVITE_REPLY, Json.object().put("inviteId", inv.text("inviteId")).put("accept", true));
        Message sa = expect(a, MessageType.GAME_START);
        Message sb = expect(b, MessageType.GAME_START);
        assertEquals("X", sa.text("symbol"));
        assertEquals("O", sb.text("symbol"));
        assertEquals(sa.text("gameId"), sb.text("gameId"));
        return sa.text("gameId");
    }

    static void move(ProtocolClient c, String gameId, int cell) throws Exception {
        c.request(MessageType.MOVE, Json.object().put("gameId", gameId).put("cell", cell));
    }

    /** Espera o GAME_STATE com {@code n} jogadas. */
    static Message expectState(ProtocolClient c, int n) throws InterruptedException {
        Message m = c.await(x -> x.type().equals(MessageType.GAME_STATE) && x.payload().get("moveCount").asInt() == n, WAIT);
        assertNotNull(m, "esperava GAME_STATE com " + n + " jogadas");
        return m;
    }

    static List<String> board(Message state) {
        List<String> b = new ArrayList<>();
        state.payload().get("board").forEach(n -> b.add(n.asText()));
        return b;
    }
}
