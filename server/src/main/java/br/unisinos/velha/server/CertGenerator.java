package br.unisinos.velha.server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gera o certificado autoassinado do servidor com o keytool do próprio JDK,
 * do mesmo jeito que scripts/gerar-certificados.sh:
 *
 * <ul>
 *   <li>{@code keystore}: par de chaves EC P-256 + certificado (fica no servidor);</li>
 *   <li>{@code server.cer}: o certificado público, na mesma pasta;</li>
 *   <li>{@code client-truststore.p12}: truststore com só esse certificado (vai para os clientes).</li>
 * </ul>
 */
public final class CertGenerator {

    public static final String ALIAS = "velha-server";
    public static final String TRUSTSTORE_NAME = "client-truststore.p12";

    private CertGenerator() {
    }

    /**
     * Cria keystore, certificado e truststore. O SAN leva localhost, 127.0.0.1,
     * os IPv4 das interfaces de rede da máquina e os {@code extraHosts}.
     */
    public static void generate(Path keystore, char[] keystorePass, char[] truststorePass,
                                String alias, String dname, int validityDays, List<String> extraHosts)
            throws IOException, InterruptedException {
        keystore = keystore.toAbsolutePath();
        Path dir = keystore.getParent();
        Files.createDirectories(dir);
        Path cer = dir.resolve(alias.equals(ALIAS) ? "server.cer" : alias + ".cer");
        Path truststore = dir.resolve(alias.equals(ALIAS) ? TRUSTSTORE_NAME : alias + "-truststore.p12");
        Files.deleteIfExists(keystore);
        Files.deleteIfExists(cer);
        Files.deleteIfExists(truststore);

        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        String ks = new String(keystorePass);
        String ts = new String(truststorePass);
        run(List.of(keytool, "-genkeypair", "-alias", alias, "-keyalg", "EC", "-groupname", "secp256r1",
                "-validity", Integer.toString(validityDays), "-dname", dname, "-ext", "SAN=" + san(extraHosts),
                "-storetype", "PKCS12", "-keystore", keystore.toString(),
                "-storepass", ks, "-keypass", ks), dir);
        run(List.of(keytool, "-exportcert", "-alias", alias, "-keystore", keystore.toString(),
                "-storepass", ks, "-file", cer.toString()), dir);
        run(List.of(keytool, "-importcert", "-noprompt", "-alias", alias, "-file", cer.toString(),
                "-storetype", "PKCS12", "-keystore", truststore.toString(), "-storepass", ts), dir);
    }

    static String san(List<String> extraHosts) {
        List<String> entries = new ArrayList<>(List.of("dns:localhost", "ip:127.0.0.1"));
        for (String ip : localIPv4()) {
            entries.add("ip:" + ip);
        }
        for (String host : extraHosts) {
            String e = (host.matches("[0-9.]+") ? "ip:" : "dns:") + host;
            if (!entries.contains(e)) {
                entries.add(e);
            }
        }
        return String.join(",", entries);
    }

    private static List<String> localIPv4() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                nif.inetAddresses()
                        .filter(Inet4Address.class::isInstance)
                        .map(a -> a.getHostAddress())
                        .forEach(ips::add);
            }
        } catch (SocketException e) {
            // sem lista de interfaces: fica só localhost/127.0.0.1
        }
        return ips;
    }

    private static void run(List<String> cmd, Path dir) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IOException("keytool falhou: " + out);
        }
    }
}
