package br.unisinos.velha.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/** Serialização JSON compartilhada por cliente e servidor. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static byte[] encode(Message m) {
        try {
            return MAPPER.writeValueAsBytes(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Decodifica um quadro; lança {@link MalformedMessageException} se não for um envelope válido. */
    public static Message decode(byte[] body) throws MalformedMessageException {
        try {
            Message m = MAPPER.readValue(body, Message.class);
            if (m.type() == null || m.type().isBlank()) {
                throw new MalformedMessageException("Campo 'type' ausente");
            }
            return m;
        } catch (JsonProcessingException e) {
            throw new MalformedMessageException("JSON inválido: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new MalformedMessageException("JSON inválido: " + e.getMessage());
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class MalformedMessageException extends Exception {
        public MalformedMessageException(String msg) {
            super(msg);
        }
    }
}
