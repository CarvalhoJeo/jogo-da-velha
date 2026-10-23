package br.unisinos.velha.server;

import br.unisinos.velha.common.net.ProtocolClient;
import br.unisinos.velha.common.protocol.ErrorCode;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Testes de ponta a ponta com clientes reais sobre TLS (docs/10-plano-de-testes.md). */
class ServerIntegrationTest extends ServerTestBase {

    // ---------- Req. 1 e 2: cadastro e autenticação ----------

    @Test
    void registerLoginDuplicateAndWrongPassword() throws Exception {
        ProtocolClient c = connect();
        c.request(MessageType.REGISTER, Json.object().put("username", "ana").put("password", PASSWORD));
        expect(c, MessageType.REGISTER_OK);

        c.request(MessageType.REGISTER, Json.object().put("username", "ANA").put("password", "outra123"));
        Message dup = expect(c, MessageType.AUTH_FAIL);
        assertEquals("Não foi possível concluir o cadastro", dup.text("reason"));

        c.request(MessageType.LOGIN, Json.object().put("username", "ana").put("password", "errada1"));
        Message wrongPw = expect(c, MessageType.AUTH_FAIL);
        c.request(MessageType.LOGIN, Json.object().put("username", "naoexiste").put("password", "errada1"));
        Message noUser = expect(c, MessageType.AUTH_FAIL);
        // Enumeração de usuários: as duas falhas são idênticas
        assertEquals(wrongPw.payload(), noUser.payload());

        long seq = c.request(MessageType.LOGIN, Json.object().put("username", "ana").put("password", PASSWORD));
        Message ok = expect(c, MessageType.AUTH_OK);
        assertEquals(seq, ok.seq(), "resposta casa com o seq do pedido");
        assertEquals(43, ok.text("token").length(), "token de 32 bytes em base64url");
    }

    @Test
    void passwordIsNotStoredInPlainText() throws Exception {
        login("ana");
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + cfg.dbPath);
             ResultSet rs = db.createStatement().executeQuery("SELECT PasswordHash, Salt, Iterations FROM Users")) {
            assertTrue(rs.next());
            assertFalse(rs.getString(1).contains(PASSWORD));
            assertNotNull(rs.getString(2));
            assertEquals(cfg.pbkdf2Iterations, rs.getInt(3));
        }
    }

    @Test
    void invalidUsernameOrShortPasswordIsRejected() throws Exception {
        ProtocolClient c = connect();
        c.request(MessageType.REGISTER, Json.object().put("username", "a b").put("password", PASSWORD));
        expect(c, MessageType.AUTH_FAIL);
        c.request(MessageType.REGISTER, Json.object().put("username", "bia").put("password", "123"));
        expect(c, MessageType.AUTH_FAIL);
    }

    @Test
    void sixWrongLoginsInAMinuteAreBlocked() throws Exception {
        login("ana").close();
        ProtocolClient c = connect();
        for (int i = 0; i < 5; i++) {
            c.request(MessageType.LOGIN, Json.object().put("username", "ana").put("password", "errada" + i));
            Message f = expect(c, MessageType.AUTH_FAIL);
            assertFalse(f.payload().has("blocked"));
        }
        // a 6a tentativa é bloqueada mesmo com a senha certa
        c.request(MessageType.LOGIN, Json.object().put("username", "ana").put("password", PASSWORD));
        Message blocked = expect(c, MessageType.AUTH_FAIL);
        assertTrue(blocked.payload().get("blocked").asBoolean());
        assertFalse(server.eventRepository().byType("LOGIN_BLOCKED").isEmpty());
    }

    @Test
    void requestsNeedValidToken() throws Exception {
        ProtocolClient anon = connect();
        anon.request(MessageType.LEADERBOARD);
        expectError(anon, ErrorCode.NOT_AUTHENTICATED);

        ProtocolClient ana = login("ana");
        ana.send(new Message(MessageType.LEADERBOARD, 99, "token-falso", Json.object()));
        expectError(ana, ErrorCode.NOT_AUTHENTICATED);
        ana.request(MessageType.LEADERBOARD);
        expect(ana, MessageType.LEADERBOARD_DATA);
    }

    @Test
    void logoutInvalidatesToken() throws Exception {
        ProtocolClient ana = login("ana");
        String token = ana.token();
        ana.request(MessageType.LOGOUT);
        ana.request(MessageType.LEADERBOARD);
        expectError(ana, ErrorCode.NOT_AUTHENTICATED);

        ProtocolClient other = connect();
        other.send(new Message(MessageType.RECONNECT, 1, token, Json.object()));
        expectError(other, ErrorCode.RECONNECT_FAILED);
    }

    @Test
    void secondLoginReplacesFirstSession() throws Exception {
        ProtocolClient first = login("ana");
        login("ana");
        expectError(first, ErrorCode.SESSION_REPLACED);
        waitUntil(() -> !first.isConnected());
    }

    // ---------- Req. 3: usuários online ----------

    @Test
    void onlineListUpdatesOnJoinAndLeave() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        assertNotNull(ana.await(m -> m.type().equals(MessageType.ONLINE_USERS) && names(m).equals(List.of("ana", "bia")), WAIT));
        assertNotNull(bia.await(m -> m.type().equals(MessageType.ONLINE_USERS) && names(m).contains("ana"), WAIT));

        bia.close();
        assertNotNull(ana.await(m -> m.type().equals(MessageType.ONLINE_USERS) && names(m).equals(List.of("ana")), WAIT));
    }

    // ---------- Req. 4 e 5: convites ----------

    @Test
    void inviteAcceptedCreatesGame() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        long seq = ana.request(MessageType.INVITE, Json.object().put("targetUser", "bia"));
        Message sent = expect(ana, MessageType.INVITE_SENT);
        assertEquals(seq, sent.seq());
        Message inv = expect(bia, MessageType.INVITE_RECEIVED);
        assertEquals("ana", inv.text("from"));
        bia.request(MessageType.INVITE_REPLY, Json.object().put("inviteId", inv.text("inviteId")).put("accept", true));
        Message start = expect(ana, MessageType.GAME_START);
        assertEquals("bia", start.text("opponent"));
        assertEquals("X", start.text("firstTurn"));
        expect(bia, MessageType.GAME_START);
        assertEquals(1, server.activeGames());
        // os dois aparecem como "em partida" na lista
        assertNotNull(ana.await(m -> m.type().equals(MessageType.ONLINE_USERS)
                && m.payload().get("users").get(0).get("inGame").asBoolean(), WAIT));
    }

    @Test
    void inviteDeclinedDoesNotCreateGame() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        ana.request(MessageType.INVITE, Json.object().put("targetUser", "bia"));
        Message inv = expect(bia, MessageType.INVITE_RECEIVED);
        bia.request(MessageType.INVITE_REPLY, Json.object().put("inviteId", inv.text("inviteId")).put("accept", false));
        Message r = expect(ana, MessageType.INVITE_RESULT);
        assertEquals("DECLINED", r.text("status"));
        assertEquals(0, server.activeGames());
    }

    @Test
    void inviteExpires() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        ana.request(MessageType.INVITE, Json.object().put("targetUser", "bia"));
        Message inv = expect(bia, MessageType.INVITE_RECEIVED);
        assertEquals("EXPIRED", expect(ana, MessageType.INVITE_RESULT).text("status"));
        assertEquals("EXPIRED", expect(bia, MessageType.INVITE_RESULT).text("status"));
        // aceitar depois de expirar não cria partida
        bia.request(MessageType.INVITE_REPLY, Json.object().put("inviteId", inv.text("inviteId")).put("accept", true));
        expectError(bia, ErrorCode.INVALID_INVITE);
        assertEquals(0, server.activeGames());
    }

    @Test
    void invalidInvites() throws Exception {
        ProtocolClient ana = login("ana");
        ana.request(MessageType.INVITE, Json.object().put("targetUser", "ninguem"));
        expectError(ana, ErrorCode.USER_OFFLINE);
        ana.request(MessageType.INVITE, Json.object().put("targetUser", "ana"));
        expectError(ana, ErrorCode.BAD_REQUEST);

        ProtocolClient bia = login("bia");
        ProtocolClient caio = login("caio");
        startGame(ana, bia, "bia");
        caio.request(MessageType.INVITE, Json.object().put("targetUser", "ana"));
        expectError(caio, ErrorCode.USER_BUSY);
    }

    // ---------- Req. 6 e 7: jogo sincronizado e resultado ----------

    @Test
    void fullGameUntilWin() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        int[] cells = {0, 3, 1, 4, 2}; // X completa a linha de cima
        for (int i = 0; i < cells.length; i++) {
            move(i % 2 == 0 ? ana : bia, g, cells[i]);
            Message sa = expectState(ana, i + 1);
            Message sb = expectState(bia, i + 1);
            assertEquals(board(sa), board(sb), "tabuleiro igual nos dois clientes");
        }
        Message overA = expect(ana, MessageType.GAME_OVER);
        Message overB = expect(bia, MessageType.GAME_OVER);
        assertEquals("WIN", overA.text("result"));
        assertEquals("LOSS", overB.text("result"));
        assertEquals("THREE_IN_ROW", overA.text("reason"));
        assertEquals("[0,1,2]", overB.payload().get("winningLine").toString());
        assertEquals("ana", overB.text("winner"));
        waitUntil(() -> server.activeGames() == 0);
        assertEquals(List.of(0, 3, 1, 4, 2), server.gameRepository().moves(Integer.parseInt(g.substring(2))));
    }

    @Test
    void oCanWin() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        play(ana, bia, g, 0, 3, 1, 4, 8, 5);
        assertEquals("LOSS", expect(ana, MessageType.GAME_OVER).text("result"));
        assertEquals("WIN", expect(bia, MessageType.GAME_OVER).text("result"));
    }

    @Test
    void fullGameUntilDraw() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        play(ana, bia, g, 0, 1, 2, 4, 3, 5, 7, 6, 8);
        Message a = expect(ana, MessageType.GAME_OVER);
        Message b = expect(bia, MessageType.GAME_OVER);
        assertEquals("DRAW", a.text("result"));
        assertEquals("DRAW", b.text("result"));
        assertEquals("DRAW", a.text("reason"));
        assertFalse(a.payload().has("winningLine"));
    }

    @Test
    void moveOutOfTurnOrOnOccupiedCellIsRejected() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        move(bia, g, 0);
        expectError(bia, ErrorCode.NOT_YOUR_TURN);
        move(ana, g, 4);
        expectState(bia, 1);
        move(bia, g, 4);
        expectError(bia, ErrorCode.INVALID_MOVE);
        move(bia, g, 9);
        expectError(bia, ErrorCode.INVALID_MOVE);
        move(bia, g, 0);
        Message s = expectState(ana, 2);
        assertEquals(List.of("O", "", "", "", "X", "", "", "", ""), board(s));
    }

    @Test
    void moveWithoutGameOrWrongGameId() throws Exception {
        ProtocolClient ana = login("ana");
        move(ana, "g-999", 0);
        expectError(ana, ErrorCode.GAME_NOT_FOUND);
    }

    // ---------- Req. 8: desistência ----------

    @Test
    void resignGivesOpponentTheWin() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        move(ana, g, 4);
        bia.request(MessageType.RESIGN, Json.object().put("gameId", g));
        Message a = expect(ana, MessageType.GAME_OVER);
        assertEquals("WIN", a.text("result"));
        assertEquals("RESIGN", a.text("reason"));
        assertEquals("LOSS", expect(bia, MessageType.GAME_OVER).text("result"));
    }

    @Test
    void logoutDuringGameConcedes() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        startGame(ana, bia, "bia");
        bia.request(MessageType.LOGOUT);
        Message a = expect(ana, MessageType.GAME_OVER);
        assertEquals("WIN", a.text("result"));
        assertEquals("RESIGN", a.text("reason"));
    }

    // ---------- Req. 9: eventos e robustez ----------

    @Test
    void eventsAreRecordedInDatabase() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        bia.request(MessageType.RESIGN, Json.object().put("gameId", g));
        expect(ana, MessageType.GAME_OVER);
        sendRaw(ana, "{isso nao e json".getBytes(StandardCharsets.UTF_8));
        expectError(ana, ErrorCode.MALFORMED);

        var repo = server.eventRepository();
        waitUntil(() -> !repo.byType("GAME_ENDED").isEmpty());
        assertFalse(repo.byType("SERVER_STARTED").isEmpty());
        assertEquals(2, repo.byType("USER_REGISTERED").size());
        assertEquals(2, repo.byType("LOGIN").size());
        assertFalse(repo.byType("INVITE_SENT").isEmpty());
        var started = repo.byType("GAME_STARTED");
        assertEquals(g, started.get(0).gameId());
        assertTrue(repo.byType("GAME_ENDED").get(0).details().contains("RESIGN"));
        assertEquals("WARN", repo.byType("MALFORMED_MESSAGE").get(0).level());
    }

    @Test
    void malformedMessageKeepsConnectionAlive() throws Exception {
        ProtocolClient ana = login("ana");
        sendRaw(ana, "não é json".getBytes(StandardCharsets.UTF_8));
        expectError(ana, ErrorCode.MALFORMED);
        sendRaw(ana, "{\"seq\":1}".getBytes(StandardCharsets.UTF_8));
        expectError(ana, ErrorCode.MALFORMED);
        ana.send(new Message("NAO_EXISTE", 5, ana.token(), Json.object()));
        expectError(ana, ErrorCode.UNKNOWN_TYPE);
        ana.request(MessageType.MOVE, Json.object().put("cell", "quatro"));
        expectError(ana, ErrorCode.BAD_REQUEST);
        long seq = ana.request(MessageType.PING);
        assertEquals(seq, expect(ana, MessageType.PONG).seq());
    }

    @Test
    void oversizedFrameClosesOnlyThatConnection() throws Exception {
        ProtocolClient ana = login("ana");
        DataOutputStream out = new DataOutputStream(ana.channel().socket().getOutputStream());
        out.writeInt(100_000);
        out.flush();
        expectError(ana, ErrorCode.FRAME_TOO_LARGE);
        waitUntil(() -> !ana.isConnected());
        // o servidor continua de pé
        ProtocolClient bia = login("bia");
        bia.request(MessageType.LEADERBOARD);
        expect(bia, MessageType.LEADERBOARD_DATA);
    }

    @Test
    void floodIsRateLimited() throws Exception {
        ProtocolClient ana = login("ana");
        for (int i = 0; i < cfg.maxMessagesPerSecond + 5; i++) {
            ana.request(MessageType.PING);
        }
        expectError(ana, ErrorCode.RATE_LIMITED);
    }

    @Test
    void serverRestartAbortsGamesAndClientsAreDisconnected() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        move(ana, g, 4);
        expectState(bia, 1);
        server.close();
        waitUntil(() -> !ana.isConnected() && !bia.isConnected());

        ServerConfig c2 = baseConfig(cfg.dbPath);
        server = new GameServer(c2);
        server.start();
        int id = Integer.parseInt(g.substring(2));
        assertEquals("ABORTED", server.gameRepository().status(id));
        assertEquals(List.of(4), server.gameRepository().moves(id), "jogadas ficam registradas");
        login("ana"); // o servidor novo aceita login normalmente
    }

    // ---------- Reconexão (opcional escolhido) ----------

    @Test
    void reconnectResumesGame() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g = startGame(ana, bia, "bia");
        move(ana, g, 4);
        expectState(bia, 1);

        String token = ana.token();
        ana.close();
        Message disc = expect(bia, MessageType.OPPONENT_DISCONNECTED);
        assertEquals("ana", disc.text("username"));
        move(bia, g, 0);
        expectError(bia, ErrorCode.GAME_PAUSED);

        ProtocolClient ana2 = connect();
        ana2.send(new Message(MessageType.RECONNECT, 1, token, Json.object().put("gameId", g)));
        Message ok = expect(ana2, MessageType.AUTH_OK);
        assertTrue(ok.payload().get("reconnected").asBoolean());
        ana2.setToken(ok.text("token"));
        Message start = expect(ana2, MessageType.GAME_START);
        assertTrue(start.payload().get("resumed").asBoolean());
        assertEquals("X", start.text("symbol"));
        Message st = expect(ana2, MessageType.GAME_STATE);
        assertEquals("X", board(st).get(4));
        expect(bia, MessageType.OPPONENT_RECONNECTED);

        move(bia, g, 0);
        expectState(ana2, 2);
    }

    @Test
    void loginAgainAlsoResumesGame() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        startGame(ana, bia, "bia");
        ana.close();
        expect(bia, MessageType.OPPONENT_DISCONNECTED);
        ProtocolClient ana2 = login("ana");
        assertTrue(expect(ana2, MessageType.GAME_START).payload().get("resumed").asBoolean());
        expect(bia, MessageType.OPPONENT_RECONNECTED);
    }

    @Test
    void opponentWinsIfDisconnectedPlayerDoesNotReturn() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        startGame(ana, bia, "bia");
        String token = ana.token();
        ana.close();
        expect(bia, MessageType.OPPONENT_DISCONNECTED);
        Message over = bia.await(MessageType.GAME_OVER, Duration.ofSeconds(5));
        assertNotNull(over);
        assertEquals("WIN", over.text("result"));
        assertEquals("TIMEOUT", over.text("reason"));

        ProtocolClient late = connect();
        late.send(new Message(MessageType.RECONNECT, 1, token, Json.object()));
        expectError(late, ErrorCode.RECONNECT_FAILED);
    }

    @Test
    void silentClientIsDroppedByHeartbeatTimeout() throws Exception {
        server.close();
        ServerConfig c = baseConfig(tmp.resolve("hb.db"));
        c.readTimeout = Duration.ofMillis(600);
        server = new GameServer(c);
        server.start();

        ProtocolClient silent = connect();
        ProtocolClient alive = connect();
        alive.startHeartbeat(Duration.ofMillis(150));
        waitUntil(() -> !silent.isConnected());
        Thread.sleep(800);
        assertTrue(alive.isConnected());
        assertFalse(server.eventRepository().byType("TIMEOUT").isEmpty());
    }

    // ---------- Placar (opcional escolhido) ----------

    @Test
    void leaderboardCountsWinsAndDraws() throws Exception {
        ProtocolClient ana = login("ana");
        ProtocolClient bia = login("bia");
        String g1 = startGame(ana, bia, "bia");
        play(ana, bia, g1, 0, 3, 1, 4, 2); // ana vence
        expect(ana, MessageType.GAME_OVER);
        expect(bia, MessageType.GAME_OVER);
        waitUntil(() -> server.activeGames() == 0);
        String g2 = startGame(ana, bia, "bia");
        play(ana, bia, g2, 0, 1, 2, 4, 3, 5, 7, 6, 8); // empate
        expect(ana, MessageType.GAME_OVER);
        waitUntil(() -> server.activeGames() == 0);

        ana.request(MessageType.LEADERBOARD);
        JsonNode rows = expect(ana, MessageType.LEADERBOARD_DATA).payload().get("rows");
        assertEquals(2, rows.size());
        assertEquals("ana", rows.get(0).get("username").asText());
        assertEquals(1, rows.get(0).get("wins").asInt());
        assertEquals(1, rows.get(0).get("draws").asInt());
        assertEquals("bia", rows.get(1).get("username").asText());
        assertEquals(0, rows.get(1).get("wins").asInt());
        assertEquals(1, rows.get(1).get("losses").asInt());
        assertEquals(2, rows.get(1).get("games").asInt());
    }

    // ---------- Segurança do canal ----------

    @Test
    void negotiatesTls13() throws Exception {
        ProtocolClient c = connect();
        assertEquals("TLSv1.3", ((SSLSocket) c.channel().socket()).getSession().getProtocol());
    }

    @Test
    void plainTextClientCannotTalkToTlsServer() throws Exception {
        try (Socket s = new Socket("localhost", server.port())) {
            s.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            byte[] body = Json.encode(Message.of(MessageType.PING));
            out.writeInt(body.length);
            out.write(body);
            out.flush();
            // o servidor trata os bytes como handshake TLS inválido e fecha
            int r;
            try {
                r = s.getInputStream().read();
            } catch (IOException e) {
                r = -1;
            }
            assertTrue(r == -1 || r == 0x15, "esperava fechamento ou alerta TLS, veio " + r);
        }
    }

    @Test
    void clientRejectsServerWithUntrustedCertificate() throws Exception {
        // "proxy MitM": servidor TLS com outro certificado, que o cliente não confia
        try (SSLServerSocket fake = (SSLServerSocket) TestCerts.mitmServerContext()
                .getServerSocketFactory().createServerSocket(0)) {
            Thread.ofVirtual().start(() -> {
                try (SSLSocket s = (SSLSocket) fake.accept()) {
                    s.startHandshake();
                } catch (IOException ignored) {
                    // esperado: o cliente aborta o handshake
                }
            });
            assertThrows(SSLException.class,
                    () -> ProtocolClient.connect("localhost", fake.getLocalPort(), TestCerts.clientContext()));
        }
    }

    // ---------- utilitários ----------

    private static void play(ProtocolClient x, ProtocolClient o, String g, int... cells) throws Exception {
        for (int i = 0; i < cells.length; i++) {
            ProtocolClient p = i % 2 == 0 ? x : o;
            move(p, g, cells[i]);
            expectState(x, i + 1);
            expectState(o, i + 1);
        }
    }

    private static List<String> names(Message onlineUsers) {
        List<String> n = new ArrayList<>();
        onlineUsers.payload().get("users").forEach(u -> n.add(u.get("username").asText()));
        return n;
    }

    private static void sendRaw(ProtocolClient c, byte[] body) throws IOException {
        DataOutputStream out = new DataOutputStream(c.channel().socket().getOutputStream());
        out.writeInt(body.length);
        out.write(body);
        out.flush();
    }

    static void waitUntil(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("condição não atingida em 5 s");
            }
            Thread.sleep(20);
        }
    }
}
