package br.unisinos.velha.client.console;

import br.unisinos.velha.client.ClientSettings;
import br.unisinos.velha.common.net.ProtocolClient;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teste de carga contra um servidor real (requisito 10): N pares de clientes
 * fazem login, iniciam partidas ao mesmo tempo e jogam até o fim.
 *
 * <pre>
 * java -cp client/target/velha-client.jar br.unisinos.velha.client.console.LoadTestMain --host 192.168.0.10 --pares 25
 * </pre>
 */
public final class LoadTestMain {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final String PASSWORD = "carga123";

    private LoadTestMain() {
    }

    public static void main(String[] args) throws Exception {
        ClientSettings s = ClientSettings.parse(Arrays.asList(args));
        int pairs = 25;
        for (int i = 0; i < s.rest().size(); i++) {
            if (s.rest().get(i).equals("--pares")) {
                pairs = Integer.parseInt(s.rest().get(++i));
            }
        }
        SSLContext tls = s.sslContext();
        String prefix = "carga" + (System.currentTimeMillis() % 100000) + "_";
        System.out.printf("Teste de carga: %d clientes, %d partidas simultâneas em %s:%d%n",
                pairs * 2, pairs, s.host(), s.port());

        long t0 = System.nanoTime();
        List<ProtocolClient> clients = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ProtocolClient>> fs = new ArrayList<>();
            for (int i = 0; i < pairs * 2; i++) {
                String name = prefix + i;
                fs.add(pool.submit(() -> login(s, tls, name)));
            }
            for (Future<ProtocolClient> f : fs) {
                clients.add(f.get());
            }
        }
        System.out.printf("%d logins em %d ms%n", clients.size(), (System.nanoTime() - t0) / 1_000_000);

        AtomicInteger finished = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long t1 = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < pairs; i++) {
                ProtocolClient x = clients.get(2 * i);
                ProtocolClient o = clients.get(2 * i + 1);
                String oName = prefix + (2 * i + 1);
                pool.submit(() -> {
                    try {
                        playOne(x, o, oName);
                        finished.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        System.err.println("Falha: " + e.getMessage());
                    }
                    return null;
                });
            }
        }
        System.out.printf("Partidas concluídas: %d, falhas: %d, tempo: %d ms%n",
                finished.get(), failed.get(), (System.nanoTime() - t1) / 1_000_000);
        clients.forEach(ProtocolClient::close);
        System.exit(failed.get() == 0 ? 0 : 1);
    }

    private static ProtocolClient login(ClientSettings s, SSLContext tls, String name) throws Exception {
        ProtocolClient c = ProtocolClient.connect(s.host(), s.port(), tls);
        c.startHeartbeat(Duration.ofSeconds(10));
        c.request(MessageType.REGISTER, Json.object().put("username", name).put("password", PASSWORD));
        c.await(m -> m.type().equals(MessageType.REGISTER_OK) || m.type().equals(MessageType.AUTH_FAIL), WAIT);
        c.request(MessageType.LOGIN, Json.object().put("username", name).put("password", PASSWORD));
        c.setToken(need(c, MessageType.AUTH_OK).text("token"));
        return c;
    }

    private static void playOne(ProtocolClient x, ProtocolClient o, String oName) throws Exception {
        x.request(MessageType.INVITE, Json.object().put("targetUser", oName));
        Message inv = need(o, MessageType.INVITE_RECEIVED);
        o.request(MessageType.INVITE_REPLY, Json.object().put("inviteId", inv.text("inviteId")).put("accept", true));
        String g = need(x, MessageType.GAME_START).text("gameId");
        need(o, MessageType.GAME_START);
        int[] cells = {4, 0, 2, 6, 3, 5, 1, 7, 8}; // termina em empate: 9 jogadas
        for (int i = 0; i < cells.length; i++) {
            (i % 2 == 0 ? x : o).request(MessageType.MOVE, Json.object().put("gameId", g).put("cell", cells[i]));
            int n = i + 1;
            for (ProtocolClient c : new ProtocolClient[]{x, o}) {
                if (c.await(m -> m.type().equals(MessageType.GAME_STATE)
                        && m.payload().get("moveCount").asInt() == n, WAIT) == null) {
                    throw new IllegalStateException("GAME_STATE " + n + " perdido em " + g);
                }
            }
        }
        need(x, MessageType.GAME_OVER);
        need(o, MessageType.GAME_OVER);
    }

    private static Message need(ProtocolClient c, String type) throws InterruptedException {
        Message m = c.await(type, WAIT);
        if (m == null) {
            throw new IllegalStateException("timeout esperando " + type);
        }
        return m;
    }
}
