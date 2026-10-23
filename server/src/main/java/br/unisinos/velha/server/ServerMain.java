package br.unisinos.velha.server;

import br.unisinos.velha.common.tls.TlsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Ponto de entrada do servidor.
 *
 * <pre>
 * java -jar server/target/velha-server.jar [--port 5555] [--db velha.db]
 *      [--keystore certs/server-keystore.p12] [--sem-tls]
 * </pre>
 *
 * A senha do keystore vem da variável de ambiente VELHA_KEYSTORE_PASSWORD.
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

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> cfg.port = Integer.parseInt(args[++i]);
                case "--db" -> cfg.dbPath = Path.of(args[++i]);
                case "--keystore" -> keystore = Path.of(args[++i]);
                case "--sem-tls" -> tls = false;
                case "-h", "--help" -> {
                    System.out.println("Uso: java -jar velha-server.jar [--port 5555] [--db velha.db] "
                            + "[--keystore certs/server-keystore.p12] [--sem-tls]");
                    return;
                }
                default -> {
                    System.err.println("Argumento desconhecido: " + args[i]);
                    System.exit(2);
                }
            }
        }

        if (tls) {
            if (!Files.exists(keystore)) {
                System.err.println("Keystore não encontrado: " + keystore
                        + "\nGere os certificados com scripts/gerar-certificados.sh (ver README).");
                System.exit(1);
            }
            String pw = System.getenv().getOrDefault("VELHA_KEYSTORE_PASSWORD", "changeit");
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
