package br.unisinos.velha.common.net;

import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageChannel;
import br.unisinos.velha.common.protocol.MessageType;
import br.unisinos.velha.common.tls.TlsConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Cliente de protocolo sem interface: conecta, envia pedidos e recebe mensagens
 * numa fila. Usado pelo cliente de console, pelo teste de carga e pelos testes
 * de ponta a ponta; o cliente JavaFX usa o modo com listener.
 */
public class ProtocolClient implements Closeable {

    private final MessageChannel channel;
    private final AtomicLong seq = new AtomicLong();
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
    private volatile Consumer<Message> listener;
    private volatile Runnable onDisconnect;
    private volatile String token;
    private final Thread reader;

    private ProtocolClient(Socket socket) throws IOException {
        this.channel = new MessageChannel(socket);
        this.reader = Thread.ofVirtual().name("client-reader").start(this::readLoop);
    }

    /** Conecta com TLS 1.3 quando {@code tls} não é nulo; sem TLS caso contrário (só para comparação no Wireshark). */
    public static ProtocolClient connect(String host, int port, SSLContext tls) throws IOException {
        Socket socket;
        if (tls != null) {
            SSLSocket s = (SSLSocket) tls.getSocketFactory().createSocket();
            TlsConfig.restrict(s);
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(10_000);
            s.startHandshake();
            s.setSoTimeout(0);
            socket = s;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
        }
        socket.setTcpNoDelay(true);
        return new ProtocolClient(socket);
    }

    private void readLoop() {
        try {
            while (true) {
                Message m = channel.receive();
                if (MessageType.PING.equals(m.type())) {
                    send(Message.of(MessageType.PONG, m.payload()));
                    continue;
                }
                Consumer<Message> l = listener;
                if (l != null) {
                    l.accept(m);
                } else {
                    inbox.add(m);
                }
            }
        } catch (IOException | Json.MalformedMessageException e) {
            // conexão encerrada
        } finally {
            channel.close();
            Runnable r = onDisconnect;
            if (r != null) {
                r.run();
            }
        }
    }

    /**
     * Envia PING periodicamente. O servidor derruba conexões sem tráfego por
     * 30 s, então isso mantém a sessão viva e permite detectar quedas.
     */
    public void startHeartbeat(Duration interval) {
        Thread.ofVirtual().name("client-heartbeat").start(() -> {
            try {
                while (isConnected()) {
                    Thread.sleep(interval);
                    send(Message.of(MessageType.PING, Json.object().put("ts", System.currentTimeMillis())));
                }
            } catch (IOException | InterruptedException e) {
                // conexão encerrada
            }
        });
    }

    /** Sem nenhuma mensagem do servidor nesse tempo, a conexão é dada como perdida. */
    public void setReadTimeout(Duration timeout) throws IOException {
        channel.socket().setSoTimeout((int) timeout.toMillis());
    }

    /** Entrega as mensagens a um listener (na thread de leitura) em vez da fila. */
    public void setListener(Consumer<Message> listener) {
        this.listener = listener;
    }

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
        if (!reader.isAlive() && onDisconnect != null) {
            onDisconnect.run();
        }
    }

    public String token() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** Envia um pedido com {@code seq} novo e o token atual; devolve o {@code seq} usado. */
    public long request(String type, ObjectNode payload) throws IOException {
        long s = seq.incrementAndGet();
        send(new Message(type, s, token, payload));
        return s;
    }

    public long request(String type) throws IOException {
        return request(type, Json.object());
    }

    public void send(Message m) throws IOException {
        channel.send(m);
    }

    /** Próxima mensagem da fila, ou {@code null} se esgotar o tempo. */
    public Message poll(Duration timeout) throws InterruptedException {
        return inbox.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Espera a primeira mensagem que satisfaz o filtro, descartando as outras. */
    public Message await(Predicate<Message> filter, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return null;
            }
            Message m = inbox.poll(left, TimeUnit.NANOSECONDS);
            if (m != null && filter.test(m)) {
                return m;
            }
        }
    }

    public Message await(String type, Duration timeout) throws InterruptedException {
        return await(m -> type.equals(m.type()), timeout);
    }

    public boolean isConnected() {
        return reader.isAlive() && !channel.isClosed();
    }

    public MessageChannel channel() {
        return channel;
    }

    @Override
    public void close() {
        channel.close();
    }
}
