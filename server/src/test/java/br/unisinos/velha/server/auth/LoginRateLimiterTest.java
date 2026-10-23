package br.unisinos.velha.server.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    /** Relógio controlado pelo teste. */
    static final class FakeClock extends Clock {
        long millis = 1_000_000;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    @Test
    void sixthAttemptInOneMinuteIsBlocked() {
        FakeClock clock = new FakeClock();
        LoginRateLimiter l = new LoginRateLimiter(5, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 5; i++) {
            assertTrue(l.tryAcquire("ana", "10.0.0.1"));
        }
        assertFalse(l.tryAcquire("ana", "10.0.0.1"));
        assertFalse(l.tryAcquire("ANA", "10.0.0.1"), "nome não diferencia maiúsculas");
        // outro IP ou outro usuário não é afetado
        assertTrue(l.tryAcquire("ana", "10.0.0.2"));
        assertTrue(l.tryAcquire("bia", "10.0.0.1"));
    }

    @Test
    void windowSlides() {
        FakeClock clock = new FakeClock();
        LoginRateLimiter l = new LoginRateLimiter(5, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 5; i++) {
            l.tryAcquire("ana", "ip");
        }
        assertFalse(l.tryAcquire("ana", "ip"));
        clock.millis += 60_000;
        assertTrue(l.tryAcquire("ana", "ip"));
    }

    @Test
    void resetClearsHistory() {
        LoginRateLimiter l = new LoginRateLimiter(2, Duration.ofMinutes(1), new FakeClock());
        l.tryAcquire("ana", "ip");
        l.tryAcquire("ana", "ip");
        assertFalse(l.tryAcquire("ana", "ip"));
        l.reset("ana", "ip");
        assertTrue(l.tryAcquire("ana", "ip"));
    }
}
