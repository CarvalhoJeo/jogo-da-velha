package br.unisinos.velha.server;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.time.Duration;

/** Parâmetros do servidor. Os padrões seguem docs/; os testes encurtam os tempos. */
public class ServerConfig {

    public int port = 5555;
    public Path dbPath = Path.of("velha.db");
    /** Contexto TLS; {@code null} só no modo de comparação sem TLS. */
    public SSLContext tls;

    public Duration inviteTimeout = Duration.ofSeconds(30);
    public Duration reconnectTimeout = Duration.ofSeconds(60);
    /** Sem nenhuma mensagem (nem PING) nesse tempo, a conexão é considerada caída. */
    public Duration readTimeout = Duration.ofSeconds(30);
    public Duration handshakeTimeout = Duration.ofSeconds(10);

    public int maxMessagesPerSecond = 20;
    public int loginMaxAttempts = 5;
    public Duration loginWindow = Duration.ofMinutes(1);
    public int pbkdf2Iterations = 210_000;
}
