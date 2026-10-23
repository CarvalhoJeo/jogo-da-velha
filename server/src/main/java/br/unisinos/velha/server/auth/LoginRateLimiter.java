package br.unisinos.velha.server.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contra força bruta: no máximo N tentativas de login por janela, por par
 * (usuário, IP). Toda tentativa conta; o login bem-sucedido limpa o histórico.
 */
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Registra uma tentativa e diz se ela é permitida. */
    public boolean tryAcquire(String username, String ip) {
        long now = clock.millis();
        Deque<Long> q = attempts.computeIfAbsent(key(username, ip), k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= window.toMillis()) {
                q.pollFirst();
            }
            if (q.size() >= maxAttempts) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    public void reset(String username, String ip) {
        attempts.remove(key(username, ip));
    }

    private static String key(String username, String ip) {
        return username.toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
