package br.unisinos.velha.server;

import br.unisinos.velha.common.tls.TlsConfig;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Gera, uma vez por execução dos testes, um keystore de servidor e um
 * truststore de cliente com o keytool do próprio JDK, do mesmo jeito que
 * scripts/gerar-certificados.sh.
 */
public final class TestCerts {

    public static final String PASSWORD = "test-pass";
    private static Path dir;

    private TestCerts() {
    }

    public static synchronized Path dir() throws Exception {
        if (dir == null) {
            dir = Files.createTempDirectory("velha-certs");
            generate(dir, "server", "server-keystore.p12", "client-truststore.p12");
            // segundo par, que o cliente NÃO confia: simula um atacante MitM
            generate(dir, "mitm", "mitm-keystore.p12", "mitm-truststore.p12");
        }
        return dir;
    }

    private static void generate(Path d, String alias, String keystore, String truststore) throws Exception {
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(List.of(keytool, "-genkeypair", "-alias", alias, "-keyalg", "EC", "-groupname", "secp256r1",
                "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", d.resolve(keystore).toString(),
                "-storepass", PASSWORD, "-keypass", PASSWORD), d);
        Path cer = d.resolve(alias + ".cer");
        run(List.of(keytool, "-exportcert", "-alias", alias, "-keystore", d.resolve(keystore).toString(),
                "-storepass", PASSWORD, "-file", cer.toString()), d);
        run(List.of(keytool, "-importcert", "-noprompt", "-alias", alias, "-file", cer.toString(),
                "-storetype", "PKCS12", "-keystore", d.resolve(truststore).toString(), "-storepass", PASSWORD), d);
    }

    private static void run(List<String> cmd, Path d) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).directory(d.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IOException("keytool falhou: " + out);
        }
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
