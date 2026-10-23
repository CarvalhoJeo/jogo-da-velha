package br.unisinos.velha.common.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Monta os contextos TLS a partir de arquivos PKCS12 gerados pelo {@code keytool}
 * (ver scripts/gerar-certificados.sh).
 *
 * <ul>
 *   <li>Servidor: keystore com o par de chaves e o certificado autoassinado.</li>
 *   <li>Cliente: truststore que contém <b>só</b> o certificado do servidor, então
 *       um proxy MitM com qualquer outro certificado é recusado no handshake.</li>
 * </ul>
 */
public final class TlsConfig {

    public static final String[] PROTOCOLS = {"TLSv1.3"};

    private TlsConfig() {
    }

    public static SSLContext serverContext(Path keystore, char[] password) throws IOException, GeneralSecurityException {
        KeyStore ks = load(keystore, password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    public static SSLContext clientContext(Path truststore, char[] password) throws IOException, GeneralSecurityException {
        KeyStore ts = load(truststore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    public static void restrict(SSLServerSocket s) {
        s.setEnabledProtocols(PROTOCOLS);
    }

    public static void restrict(SSLSocket s) {
        s.setEnabledProtocols(PROTOCOLS);
    }

    private static KeyStore load(Path file, char[] password) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            ks.load(in, password);
        }
        return ks;
    }
}
