package br.unisinos.velha.server.presence;

import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import br.unisinos.velha.server.net.ConnectionHandler;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Usuários online (requisito 3) e broadcast de ONLINE_USERS a cada mudança. */
public class PresenceService {

    private final Map<String, ConnectionHandler> online = new ConcurrentHashMap<>();
    private volatile Predicate<String> inGame = u -> false;

    public void setInGameCheck(Predicate<String> inGame) {
        this.inGame = inGame;
    }

    /** Marca a conexão como online; devolve a conexão anterior do mesmo usuário, se havia. */
    public ConnectionHandler add(ConnectionHandler conn) {
        return online.put(key(conn.username()), conn);
    }

    /** Remove só se esta ainda for a conexão registrada (não remove uma sessão mais nova). */
    public boolean remove(ConnectionHandler conn) {
        String name = conn.username();
        return name != null && online.remove(key(name), conn);
    }

    public ConnectionHandler get(String username) {
        return username == null ? null : online.get(key(username));
    }

    public boolean isCurrent(ConnectionHandler conn) {
        return conn.username() != null && get(conn.username()) == conn;
    }

    public int count() {
        return online.size();
    }

    public void broadcast() {
        Message m = snapshot();
        for (ConnectionHandler c : online.values()) {
            c.send(m);
        }
    }

    public Message snapshot() {
        Map<String, Boolean> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ConnectionHandler c : online.values()) {
            String name = c.username();
            if (name != null) {
                sorted.put(name, inGame.test(name));
            }
        }
        ArrayNode users = Json.array();
        sorted.forEach((name, busy) -> users.add(Json.object().put("username", name).put("inGame", busy)));
        ObjectNode p = Json.object();
        p.set("users", users);
        return Message.of(MessageType.ONLINE_USERS, p);
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
