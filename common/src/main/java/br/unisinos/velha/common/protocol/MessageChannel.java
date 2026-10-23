package br.unisinos.velha.common.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Canal de mensagens enquadradas sobre um socket (TLS ou não).
 * Envio é thread-safe; leitura deve ser feita por uma única thread.
 */
public class MessageChannel implements Closeable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public MessageChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void send(Message m) throws IOException {
        byte[] body = Json.encode(m);
        synchronized (out) {
            FrameCodec.write(out, body);
        }
    }

    /** Lê o próximo quadro bruto. O chamador decide como tratar JSON malformado. */
    public byte[] receiveFrame() throws IOException {
        return FrameCodec.read(in);
    }

    public Message receive() throws IOException, Json.MalformedMessageException {
        return Json.decode(receiveFrame());
    }

    public Socket socket() {
        return socket;
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // já fechado
        }
    }
}
