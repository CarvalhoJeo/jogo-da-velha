package br.unisinos.velha.client;

import br.unisinos.velha.common.net.ProtocolClient;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Duration;

/**
 * Conexão do cliente gráfico. Guarda o token e a partida ativa e, se a
 * conexão cai no meio de uma partida, tenta reconectar por até 60 s
 * enviando RECONNECT (opcional de reconexão).
 *
 * Os callbacks chegam em threads de rede; a interface repassa para a thread do JavaFX.
 */
public class ServerConnection {

    public interface Listener {
        void onMessage(Message m);

        /** Conexão perdida sem partida ativa, ou reconexão impossível. */
        void onConnectionLost(String reason);

        void onReconnecting(int attempt);
    }

    static final Duration HEARTBEAT = Duration.ofSeconds(10);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    static final Duration RECONNECT_WINDOW = Duration.ofSeconds(60);
    static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final Listener listener;
    private volatile ClientSettings settings;
    private volatile ProtocolClient client;
    private volatile String token;
    private volatile String activeGameId;
    private volatile boolean userClosed;

    public ServerConnection(ClientSettings settings, Listener listener) {
        this.settings = settings;
        this.listener = listener;
    }

    public synchronized void connect(String host, int port) throws Exception {
        if (client != null && client.isConnected()) {
            return;
        }
        settings = settings.withServer(host, port);
        userClosed = false;
        open();
    }

    private void open() throws Exception {
        ProtocolClient c = ProtocolClient.connect(settings.host(), settings.port(), settings.sslContext());
        c.setReadTimeout(READ_TIMEOUT);
        c.setListener(this::dispatch);
        client = c;
        c.setOnDisconnect(() -> lost(c));
        c.startHeartbeat(HEARTBEAT);
    }

    private void dispatch(Message m) {
        switch (m.type()) {
            case MessageType.AUTH_OK -> token = m.text("token");
            case MessageType.GAME_START -> activeGameId = m.text("gameId");
            case MessageType.GAME_OVER -> activeGameId = null;
            default -> { }
        }
        listener.onMessage(m);
    }

    private void lost(ProtocolClient which) {
        if (userClosed || which != client) {
            return;
        }
        if (activeGameId != null && token != null) {
            Thread.ofVirtual().name("reconnect").start(this::reconnectLoop);
        } else {
            token = null;
            listener.onConnectionLost("Conexão com o servidor perdida.");
        }
    }

    private void reconnectLoop() {
        long deadline = System.nanoTime() + RECONNECT_WINDOW.toNanos();
        int attempt = 0;
        while (!userClosed && System.nanoTime() < deadline) {
            attempt++;
            listener.onReconnecting(attempt);
            try {
                open();
                client.send(new Message(MessageType.RECONNECT, 0, token,
                        Json.object().put("gameId", activeGameId)));
                return; // AUTH_OK / GAME_START / ERROR chegam pelo listener
            } catch (Exception e) {
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
        if (!userClosed) {
            activeGameId = null;
            token = null;
            listener.onConnectionLost("Não foi possível reconectar ao servidor.");
        }
    }

    public void request(String type, ObjectNode payload) {
        ProtocolClient c = client;
        if (c == null) {
            return;
        }
        try {
            c.send(new Message(type, 0, token, payload));
        } catch (IOException e) {
            // a queda é tratada pelo onDisconnect
        }
    }

    public void request(String type) {
        request(type, Json.object());
    }

    public void logout() {
        request(MessageType.LOGOUT);
        token = null;
        activeGameId = null;
    }

    /** Esquece a sessão local (ex.: RECONNECT recusado). */
    public void forgetSession() {
        token = null;
        activeGameId = null;
    }

    public boolean isConnected() {
        ProtocolClient c = client;
        return c != null && c.isConnected();
    }

    public void close() {
        userClosed = true;
        ProtocolClient c = client;
        if (c != null) {
            c.close();
        }
    }
}
