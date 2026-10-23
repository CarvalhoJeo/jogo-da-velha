package br.unisinos.velha.server.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de senha com PBKDF2WithHmacSHA256, salt aleatório de 16 bytes por usuário.
 * (O enunciado cita "SHA-64", que não existe; usamos SHA-256 dentro do PBKDF2.)
 */
public class PasswordHasher {

    public record HashedPassword(String hash, String salt, int iterations) {
    }

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    public HashedPassword hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return new HashedPassword(b64.encodeToString(key), b64.encodeToString(salt), iterations);
    }

    public boolean verify(String password, HashedPassword stored) {
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] expected = b64.decode(stored.hash());
        byte[] actual = derive(password, b64.decode(stored.salt()), stored.iterations());
        return MessageDigest.isEqual(expected, actual); // comparação em tempo constante
    }

    /**
     * Gasta o mesmo tempo de uma verificação real. Usado quando o usuário não
     * existe, para que o tempo de resposta não revele quais nomes estão cadastrados.
     */
    public void burn(String password) {
        derive(password, new byte[SALT_BYTES], iterations);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }
}
