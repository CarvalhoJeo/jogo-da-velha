package br.unisinos.velha.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientSettingsTest {

    @Test
    void defaults() {
        ClientSettings s = ClientSettings.parse(List.of());
        assertEquals("localhost", s.host());
        assertEquals(5555, s.port());
        assertTrue(s.tls());
        assertEquals(ClientSettings.DEFAULT_TRUSTSTORE, s.truststore());
    }

    @Test
    void parsesOptionsAndKeepsTheRest() {
        ClientSettings s = ClientSettings.parse(List.of("--host", "10.0.0.5", "--port", "6000",
                "--truststore", "x.p12", "--sem-tls", "--pares", "3"));
        assertEquals("10.0.0.5", s.host());
        assertEquals(6000, s.port());
        assertEquals(Path.of("x.p12"), s.truststore());
        assertFalse(s.tls());
        assertEquals(List.of("--pares", "3"), s.rest());
    }

    @Test
    void missingTruststoreGivesClearMessage() {
        ClientSettings s = ClientSettings.parse(List.of("--truststore", "nao-existe.p12"));
        Exception e = assertThrows(IllegalStateException.class, s::sslContext);
        assertTrue(e.getMessage().contains("client-truststore.p12"));
    }

    @Test
    void noTlsMeansNoContext() throws Exception {
        assertNull(ClientSettings.parse(List.of("--sem-tls")).sslContext());
    }
}
