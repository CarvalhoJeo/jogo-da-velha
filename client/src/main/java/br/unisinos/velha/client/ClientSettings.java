package br.unisinos.velha.client;

import br.unisinos.velha.common.tls.TlsConfig;

import javax.net.ssl.SSLContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opções de linha de comando comuns aos clientes:
 * {@code --host localhost --port 5555 --truststore certs/client-truststore.p12 --sem-tls}.
 * A senha do truststore vem de VELHA_TRUSTSTORE_PASSWORD.
 */
public record ClientSettings(String host, int port, Path truststore, boolean tls, List<String> rest) {

    public static final Path DEFAULT_TRUSTSTORE = Path.of("certs/client-truststore.p12");

    public static ClientSettings parse(List<String> args) {
        String host = "localhost";
        int port = 5555;
        Path truststore = DEFAULT_TRUSTSTORE;
        boolean tls = true;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            switch (a) {
                case "--host" -> host = args.get(++i);
                case "--port" -> port = Integer.parseInt(args.get(++i));
                case "--truststore" -> truststore = Path.of(args.get(++i));
                case "--sem-tls" -> tls = false;
                default -> rest.add(a);
            }
        }
        return new ClientSettings(host, port, truststore, tls, List.copyOf(rest));
    }

    public ClientSettings withServer(String newHost, int newPort) {
        return new ClientSettings(newHost, newPort, truststore, tls, rest);
    }

    /** Contexto TLS que confia só no certificado do servidor, ou {@code null} sem TLS. */
    public SSLContext sslContext() throws Exception {
        if (!tls) {
            return null;
        }
        if (!Files.exists(truststore)) {
            throw new IllegalStateException("Truststore não encontrado: " + truststore.toAbsolutePath()
                    + ". Gere com scripts/gerar-certificados.sh (ver README).");
        }
        String pw = System.getenv().getOrDefault("VELHA_TRUSTSTORE_PASSWORD", "changeit");
        return TlsConfig.clientContext(truststore, pw.toCharArray());
    }
}
