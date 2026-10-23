package br.unisinos.velha.server;

import br.unisinos.velha.common.net.ProtocolClient;
import br.unisinos.velha.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** Req. 10: 50 clientes e 25 partidas simultâneas, sem perda de mensagens. */
class LoadTest extends ServerTestBase {

    static final int PAIRS = 25;

    @Test
    void fiftyClientsTwentyFiveSimultaneousGames() throws Exception {
        List<ProtocolClient[]> pairs = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ProtocolClient>> logins = new ArrayList<>();
            for (int i = 0; i < PAIRS * 2; i++) {
                String name = "user" + i;
                logins.add(pool.submit(() -> login(name)));
            }
            for (int i = 0; i < PAIRS; i++) {
                pairs.add(new ProtocolClient[]{logins.get(2 * i).get(), logins.get(2 * i + 1).get()});
            }
        }
        assertEquals(PAIRS * 2, server.onlineCount());

        // todas as partidas começam antes de qualquer uma terminar
        List<String> gameIds = new ArrayList<>();
        for (int i = 0; i < PAIRS; i++) {
            gameIds.add(startGame(pairs.get(i)[0], pairs.get(i)[1], "user" + (2 * i + 1)));
        }
        assertEquals(PAIRS, server.activeGames());

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> games = new ArrayList<>();
            for (int i = 0; i < PAIRS; i++) {
                ProtocolClient x = pairs.get(i)[0];
                ProtocolClient o = pairs.get(i)[1];
                String g = gameIds.get(i);
                games.add(pool.submit(() -> {
                    int[] cells = {0, 3, 1, 4, 2};
                    for (int m = 0; m < cells.length; m++) {
                        move(m % 2 == 0 ? x : o, g, cells[m]);
                        expectState(x, m + 1);
                        expectState(o, m + 1);
                    }
                    assertEquals("WIN", expect(x, MessageType.GAME_OVER).text("result"));
                    assertEquals("LOSS", expect(o, MessageType.GAME_OVER).text("result"));
                    return null;
                }));
            }
            for (Future<?> f : games) {
                f.get(); // propaga falhas
            }
        }
        ServerIntegrationTest.waitUntil(() -> server.activeGames() == 0);
        int wins = server.gameRepository().leaderboard(100).stream().mapToInt(r -> r.wins()).sum();
        assertEquals(PAIRS, wins);
        assertEquals(PAIRS * 2, server.connectionCount(), "nenhuma conexão caiu");
    }
}
