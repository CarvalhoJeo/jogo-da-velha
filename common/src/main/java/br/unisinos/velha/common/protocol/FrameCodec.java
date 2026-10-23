package br.unisinos.velha.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Enquadramento das mensagens sobre o fluxo de bytes do TCP.
 *
 * <pre>
 * +----------------+-------------------------------+
 * | tamanho (4 B)  | JSON UTF-8 (tamanho bytes)    |
 * +----------------+-------------------------------+
 * </pre>
 *
 * O tamanho é big-endian ({@link DataOutputStream#writeInt}). Quadros acima de
 * {@link #MAX_FRAME_BYTES} são rejeitados antes de qualquer alocação.
 */
public final class FrameCodec {

    public static final int MAX_FRAME_BYTES = 64 * 1024;

    private FrameCodec() {
    }

    public static void write(DataOutputStream out, byte[] body) throws IOException {
        if (body.length > MAX_FRAME_BYTES) {
            throw new FrameTooLargeException(body.length);
        }
        out.writeInt(body.length);
        out.write(body);
        out.flush();
    }

    /** Lê um quadro completo. Lança {@link java.io.EOFException} se a conexão fechar. */
    public static byte[] read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new FrameTooLargeException(length);
        }
        byte[] body = new byte[length];
        in.readFully(body);
        return body;
    }

    public static final class FrameTooLargeException extends IOException {
        public FrameTooLargeException(int length) {
            super("Quadro inválido ou maior que " + MAX_FRAME_BYTES + " bytes: " + length);
        }
    }
}
