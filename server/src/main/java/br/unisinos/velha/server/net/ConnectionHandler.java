package br.unisinos.velha.server.net;

import br.unisinos.velha.common.protocol.ErrorCode;
import br.unisinos.velha.common.protocol.FrameCodec;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageChannel;
import br.unisinos.velha.server.ServerConfig;
import br.unisinos.velha.server.auth.AuthService.AuthUser;
import br.unisinos.velha.server.events.EventLogger;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Uma conexão de cliente, executada numa virtual thread própria. Faz o
 * handshake TLS, lê quadros, entrega cada mensagem ao {@link MessageRouter} e
 * detecta desconexão (fim do fluxo, erro ou falta de heartbeat).
 *
 * Nenhuma exceção vinda do cliente derruba o servidor: no pior caso só esta
 * conexão é encerrada.
 */
public class ConnectionHandler implements Runnable {

    private final long id;
    private final Socket socket;
    private final String remoteIp;
    private final MessageRouter router;
    private final ServerConfig cfg;
    private final EventLogger events;
    private MessageChannel channel;

    private volatile AuthUser user;
    private volatile String token;
    private volatile boolean closed;

    // limite de mensagens por segundo (janela fixa); só a thread de leitura mexe nisso
    private long windowStart;
    private int windowCount;

    public ConnectionHandler(long id, Socket socket, MessageRouter router, ServerConfig cfg, EventLogger events) {
        this.id = id;
        this.socket = socket;
        this.remoteIp = socket.getInetAddress().getHostAddress();
        this.router = router;
        this.cfg = cfg;
        this.events = events;
    }

    @Override
    public void run() {
        try {
            if (socket instanceof SSLSocket ssl) {
                ssl.setSoTimeout((int) cfg.handshakeTimeout.toMillis());
                ssl.startHandshake();
            }
            socket.setSoTimeout((int) cfg.readTimeout.toMillis());
            socket.setTcpNoDelay(true);
            channel = new MessageChannel(socket);
            events.info("CONNECTION_OPENED", null, null, "conn", id, "ip", remoteIp,
                    "tls", socket instanceof SSLSocket);
            readLoop();
        } catch (SSLException e) {
            events.warn("TLS_HANDSHAKE_FAILED", null, null, "conn", id, "ip", remoteIp, "error", e.getMessage());
        } catch (SocketTimeoutException e) {
            events.warn("TIMEOUT", userId(), null, "conn", id, "ip", remoteIp, "username", username());
        } catch (FrameCodec.FrameTooLargeException e) {
            events.warn("FRAME_TOO_LARGE", userId(), null, "conn", id, "ip", remoteIp, "error", e.getMessage());
            send(Message.error(0, ErrorCode.FRAME_TOO_LARGE, "Quadro maior que 64 KB; conexão encerrada"));
        } catch (EOFException e) {
            // cliente fechou a conexão
        } catch (IOException e) {
            if (!closed) {
                events.warn("NETWORK_ERROR", userId(), null, "conn", id, "ip", remoteIp, "error", e.getMessage());
            }
        } catch (RuntimeException e) {
            events.error("ERROR", userId(), null, "conn", id, "error", e.toString());
        } finally {
            close();
            events.info("CONNECTION_CLOSED", userId(), null, "conn", id, "ip", remoteIp, "username", username());
            router.onDisconnect(this);
        }
    }

    private void readLoop() throws IOException {
        while (!closed) {
            byte[] frame = channel.receiveFrame();
            if (!withinRateLimit()) {
                continue;
            }
            Message m;
            try {
                m = Json.decode(frame);
            } catch (Json.MalformedMessageException e) {
                events.warn("MALFORMED_MESSAGE", userId(), null, "conn", id, "error", e.getMessage());
                send(Message.error(0, ErrorCode.MALFORMED, e.getMessage()));
                continue;
            }
            try {
                router.route(this, m);
            } catch (IllegalArgumentException e) {
                send(Message.error(m.seq(), ErrorCode.BAD_REQUEST, e.getMessage()));
            } catch (RuntimeException e) {
                events.error("ERROR", userId(), null, "conn", id, "type", m.type(), "error", e.toString());
                send(Message.error(m.seq(), ErrorCode.INTERNAL, "Erro interno"));
            }
        }
    }

    /** Descarta mensagens acima do limite; quem passa de 3x o limite na mesma janela é desconectado. */
    private boolean withinRateLimit() throws IOException {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            windowStart = now;
            windowCount = 0;
        }
        windowCount++;
        if (windowCount <= cfg.maxMessagesPerSecond) {
            return true;
        }
        if (windowCount == cfg.maxMessagesPerSecond + 1) {
            events.warn("RATE_LIMITED", userId(), null, "conn", id, "ip", remoteIp);
            send(Message.error(0, ErrorCode.RATE_LIMITED, "Mensagens demais por segundo"));
        }
        if (windowCount > cfg.maxMessagesPerSecond * 3) {
            throw new IOException("Flood: conexão encerrada");
        }
        return false;
    }

    /** Envia sem lançar exceção; se a escrita falhar, a conexão é encerrada. */
    public void send(Message m) {
        MessageChannel ch = channel;
        if (ch == null || closed) {
            return;
        }
        try {
            ch.send(m);
        } catch (IOException e) {
            close();
        }
    }

    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // já fechado
        }
    }

    public void authenticate(AuthUser user, String token) {
        this.user = user;
        this.token = token;
    }

    public void deauthenticate() {
        this.user = null;
        this.token = null;
    }

    public AuthUser user() {
        return user;
    }

    public String token() {
        return token;
    }

    public String username() {
        AuthUser u = user;
        return u == null ? null : u.username();
    }

    public Integer userId() {
        AuthUser u = user;
        return u == null ? null : u.id();
    }

    public String remoteIp() {
        return remoteIp;
    }

    public long id() {
        return id;
    }

    public boolean isClosed() {
        return closed;
    }
}
