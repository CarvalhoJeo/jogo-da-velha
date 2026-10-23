package br.unisinos.velha.server.auth;

import br.unisinos.velha.server.auth.PasswordHasher.HashedPassword;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Req. 1: senha nunca armazenada em texto; salt por usuário. */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(10_000);

    @Test
    void sameSaltNeverRepeatsAndHashesDiffer() {
        HashedPassword a = hasher.hash("segredo123");
        HashedPassword b = hasher.hash("segredo123");
        assertNotEquals(a.salt(), b.salt());
        assertNotEquals(a.hash(), b.hash());
        assertEquals(16, Base64.getDecoder().decode(a.salt()).length);
        assertEquals(32, Base64.getDecoder().decode(a.hash()).length);
    }

    @Test
    void verifiesCorrectPasswordOnly() {
        HashedPassword h = hasher.hash("segredo123");
        assertTrue(hasher.verify("segredo123", h));
        assertFalse(hasher.verify("segredo124", h));
        assertFalse(hasher.verify("", h));
    }

    @Test
    void hashDoesNotContainPassword() {
        HashedPassword h = hasher.hash("segredo123");
        assertFalse(h.hash().contains("segredo"));
        assertFalse(new String(Base64.getDecoder().decode(h.hash())).contains("segredo"));
    }

    @Test
    void verifyUsesStoredIterations() {
        HashedPassword old = new PasswordHasher(5_000).hash("abcdef");
        assertEquals(5_000, old.iterations());
        assertTrue(hasher.verify("abcdef", old));
    }

    @Test
    void defaultIterationsFollowPlan() {
        assertEquals(210_000, new br.unisinos.velha.server.ServerConfig().pbkdf2Iterations);
    }
}
