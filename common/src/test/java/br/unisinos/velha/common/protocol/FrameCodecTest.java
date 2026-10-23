package br.unisinos.velha.common.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {

    @Test
    void roundTripKeepsMessageBoundaries() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        Message a = new Message(MessageType.MOVE, 42, "tok", Json.object().put("gameId", "g-17").put("cell", 4));
        Message b = Message.of(MessageType.PING);
        FrameCodec.write(out, Json.encode(a));
        FrameCodec.write(out, Json.encode(b));

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buf.toByteArray()));
        Message ra = Json.decode(FrameCodec.read(in));
        Message rb = Json.decode(FrameCodec.read(in));
        assertEquals("MOVE", ra.type());
        assertEquals(42, ra.seq());
        assertEquals("tok", ra.token());
        assertEquals(4, ra.integer("cell"));
        assertEquals("PING", rb.type());
        assertThrows(EOFException.class, () -> FrameCodec.read(in));
    }

    @Test
    void headerIsBigEndianLength() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        FrameCodec.write(new DataOutputStream(buf), "{}".getBytes(StandardCharsets.UTF_8));
        byte[] bytes = buf.toByteArray();
        assertArrayEquals(new byte[]{0, 0, 0, 2, '{', '}'}, bytes);
    }

    @Test
    void rejectsFrameLargerThan64KB() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        assertThrows(FrameCodec.FrameTooLargeException.class,
                () -> FrameCodec.write(out, new byte[FrameCodec.MAX_FRAME_BYTES + 1]));

        byte[] header = {0x7f, 0, 0, 0}; // ~2 GB anunciados
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(header));
        assertThrows(FrameCodec.FrameTooLargeException.class, () -> FrameCodec.read(in));
    }

    @Test
    void rejectsNegativeLength() {
        byte[] header = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(header));
        assertThrows(IOException.class, () -> FrameCodec.read(in));
    }

    @Test
    void malformedJsonIsReported() {
        assertThrows(Json.MalformedMessageException.class,
                () -> Json.decode("nao e json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(Json.MalformedMessageException.class,
                () -> Json.decode("{\"seq\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingPayloadFieldGivesClearError() throws Exception {
        Message m = Json.decode("{\"type\":\"MOVE\",\"seq\":1,\"payload\":{\"cell\":\"x\"}}".getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> m.integer("cell"));
        assertTrue(e.getMessage().contains("cell"));
        assertThrows(IllegalArgumentException.class, () -> m.text("gameId"));
    }
}
