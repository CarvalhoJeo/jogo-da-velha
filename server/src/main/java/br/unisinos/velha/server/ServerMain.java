package br.unisinos.velha.server;

import br.unisinos.velha.common.tls.TlsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Ponto de entrada do servidor.
 *
 * <pre>
 * java -jar server/target/velha-server.jar [--port 5555] [--db velha.db]
 *      [--keystore certs/server-keystore.p12] [--novo-certificado] [--sem-tls]
 * </pre>
 *
 * Se o keystore não existir, o certificado autoassinado é gerado na hora
 * (ver {@link CertGenerator}); {@code --novo-certificado} força gerar outro.
 * As senhas vêm de VELHA_KEYSTORE_PASSWORD e VELHA_TRUSTSTORE_PASSWORD.
 * {@code --sem-tls} existe só para a captura "antes do TLS" no Wireshark.
 */
public final class ServerMain {

    private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig cfg = new ServerConfig();
        Path keystore = Path.of("certs/server-keystore.p12");
        boolean tls = true;
        boolean newCert = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> cfg.port = Integer.parseInt(args[++i]);
                case "--db" -> cfg.dbPath = Path.of(args[++i]);
                case "--keystore" -> keystore = Path.of(args[++i]);
                case "--novo-certificado" -> newCert = true;
                case "--sem-tls" -> tls = false;
                case "-h", "--help" -> {
                    System.out.println("Uso: java -jar velha-server.jar [--port 5555] [--db velha.db] "
                            + "[--keystore certs/server-keystore.p12] [--novo-certificado] [--sem-tls]");
                    return;
                }
                default -> {
                    System.err.println("Argumento desconhecido: " + args[i]);
                    System.exit(2);
                }
            }
        }

        if (tls) {
            String pw = System.getenv().getOrDefault("VELHA_KEYSTORE_PASSWORD", "changeit");
            if (newCert || !Files.exists(keystore)) {
                String tsPw = System.getenv().getOrDefault("VELHA_TRUSTSTORE_PASSWORD", "changeit");
                CertGenerator.generate(keystore, pw.toCharArray(), tsPw.toCharArray(), CertGenerator.ALIAS,
                        "CN=Jogo da Velha, OU=Redes, O=Unisinos, C=BR", 365, List.of());
                LOG.info("Certificado autoassinado gerado em {}. Copie {} para cada máquina cliente.",
                        keystore.toAbsolutePath().getParent(),
                        keystore.resolveSibling(CertGenerator.TRUSTSTORE_NAME));
            }
            cfg.tls = TlsConfig.serverContext(keystore, pw.toCharArray());
        } else {
            LOG.warn("*** MODO SEM TLS: todo o tráfego, inclusive senhas, vai em texto claro. Use só para a captura de comparação. ***");
        }

        GameServer server = new GameServer(cfg);
        server.start();
        LOG.info("Servidor ouvindo na porta {} ({})", server.port(), tls ? "TLS 1.3" : "sem TLS");

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            stop.countDown();
        }));
        stop.await();
    }
}
