package br.unisinos.velha.server.auth;

import br.unisinos.velha.server.db.UserRepository;
import br.unisinos.velha.server.db.UserRepository.UserRecord;
import br.unisinos.velha.server.events.EventLogger;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Cadastro, login e tokens de sessão (requisitos 1 e 2). */
public class AuthService {

    public record AuthUser(int id, String username) {
    }

    public enum LoginStatus { OK, FAIL, BLOCKED }

    public record LoginResult(LoginStatus status, AuthUser user) {
    }

    /** Token emitido no login; continua válido por um tempo após a queda só para RECONNECT. */
    private static final class TokenInfo {
        final AuthUser user;
        volatile long disconnectedAtMillis; // 0 = conexão ativa

        TokenInfo(AuthUser user) {
            this.user = user;
        }
    }

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final LoginRateLimiter limiter;
    private final EventLogger events;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public AuthService(UserRepository users, PasswordHasher hasher, LoginRateLimiter limiter, EventLogger events) {
        this.users = users;
        this.hasher = hasher;
        this.limiter = limiter;
        this.events = events;
    }

    public static boolean validUsername(String u) {
        return u != null && USERNAME.matcher(u).matches();
    }

    public static boolean validPassword(String p) {
        return p != null && p.length() >= 6 && p.length() <= 128;
    }

    /** Devolve o usuário criado, ou vazio se o nome é inválido ou já existe. */
    public Optional<AuthUser> register(String username, String password) {
        if (!validUsername(username) || !validPassword(password)) {
            return Optional.empty();
        }
        Optional<Integer> id = users.create(username, hasher.hash(password));
        id.ifPresent(i -> events.info("USER_REGISTERED", i, null, "username", username));
        return id.map(i -> new AuthUser(i, username));
    }

    public LoginResult login(String username, String password, String ip) {
        if (!limiter.tryAcquire(username, ip)) {
            events.warn("LOGIN_BLOCKED", null, null, "username", username, "ip", ip);
            return new LoginResult(LoginStatus.BLOCKED, null);
        }
        Optional<UserRecord> rec = users.findByUsername(username);
        if (rec.isEmpty()) {
            hasher.burn(password);
            events.warn("LOGIN_FAILED", null, null, "username", username, "ip", ip);
            return new LoginResult(LoginStatus.FAIL, null);
        }
        UserRecord u = rec.get();
        if (!hasher.verify(password, u.password())) {
            events.warn("LOGIN_FAILED", u.id(), null, "username", username, "ip", ip);
            return new LoginResult(LoginStatus.FAIL, null);
        }
        limiter.reset(username, ip);
        users.touchLastLogin(u.id());
        events.info("LOGIN", u.id(), null, "username", u.username(), "ip", ip);
        return new LoginResult(LoginStatus.OK, new AuthUser(u.id(), u.username()));
    }

    /** Emite um token aleatório de 32 bytes e invalida os tokens anteriores do usuário. */
    public String issueToken(AuthUser user) {
        tokens.values().removeIf(t -> t.user.id() == user.id());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.put(token, new TokenInfo(user));
        return token;
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** A conexão caiu, mas há partida em andamento: o token vale para RECONNECT até o prazo. */
    public void markDisconnected(String token) {
        TokenInfo t = tokens.get(token);
        if (t != null) {
            t.disconnectedAtMillis = System.currentTimeMillis();
        }
    }

    /**
     * Retoma a sessão de um token, se ele existe e a queda foi há menos de
     * {@code windowMillis}. Também aceita o token se a conexão antiga ainda
     * não foi detectada como caída (o chamador fecha a antiga).
     */
    public Optional<AuthUser> resume(String token, long windowMillis) {
        if (token == null) {
            return Optional.empty();
        }
        TokenInfo t = tokens.get(token);
        if (t == null) {
            return Optional.empty();
        }
        long at = t.disconnectedAtMillis;
        if (at != 0 && System.currentTimeMillis() - at > windowMillis) {
            tokens.remove(token);
            return Optional.empty();
        }
        t.disconnectedAtMillis = 0;
        return Optional.of(t.user);
    }
}
