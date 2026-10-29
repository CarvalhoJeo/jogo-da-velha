package br.unisinos.velha.server;

import br.unisinos.velha.common.tls.TlsConfig;

import javax.net.ssl.SSLContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Gera, uma vez por execução dos testes, um keystore de servidor e um
 * truststore de cliente com o {@link CertGenerator}, o mesmo que o servidor
 * usa ao subir sem certificado.
 */
public final class TestCerts {

    public static final String PASSWORD = "test-pass";
    private static Path dir;

    private TestCerts() {
    }

    public static synchronized Path dir() throws Exception {
        if (dir == null) {
            dir = Files.createTempDirectory("velha-certs");
            generate(dir, CertGenerator.ALIAS, "server-keystore.p12");
            // segundo par, que o cliente NÃO confia: simula um atacante MitM
            generate(dir, "mitm", "mitm-keystore.p12");
        }
        return dir;
    }

    private static void generate(Path d, String alias, String keystore) throws Exception {
        CertGenerator.generate(d.resolve(keystore), PASSWORD.toCharArray(), PASSWORD.toCharArray(),
                alias, "CN=localhost", 2, List.of());
    }

    public static SSLContext serverContext() throws Exception {
        return TlsConfig.serverContext(dir().resolve("server-keystore.p12"), PASSWORD.toCharArray());
    }

    public static SSLContext mitmServerContext() throws Exception {
        return TlsConfig.serverContext(dir().resolve("mitm-keystore.p12"), PASSWORD.toCharArray());
    }

    public static SSLContext clientContext() throws Exception {
        return TlsConfig.clientContext(dir().resolve("client-truststore.p12"), PASSWORD.toCharArray());
    }
}
