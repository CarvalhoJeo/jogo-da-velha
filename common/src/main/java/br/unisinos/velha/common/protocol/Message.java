package br.unisinos.velha.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Envelope fixo de toda mensagem:
 * {@code { "type": "MOVE", "seq": 42, "token": "...", "payload": { ... } }}.
 *
 * {@code seq} casa respostas com pedidos: o servidor responde com o mesmo
 * {@code seq} do pedido, e usa 0 em mensagens que ele mesmo inicia.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(String type, long seq, String token, ObjectNode payload) {

    public Message {
        if (payload == null) {
            payload = Json.object();
        }
    }

    public static Message of(String type, ObjectNode payload) {
        return new Message(type, 0, null, payload);
    }

    public static Message of(String type) {
        return new Message(type, 0, null, Json.object());
    }

    public static Message error(long seq, String code, String text) {
        ObjectNode p = Json.object().put("code", code).put("message", text);
        return new Message(MessageType.ERROR, seq, null, p);
    }

    public Message withSeq(long newSeq) {
        return new Message(type, newSeq, token, payload);
    }

    public Message withToken(String newToken) {
        return new Message(type, seq, newToken, payload);
    }

    // Leitura do payload, com erro claro quando o campo falta ou tem tipo errado.

    public String text(String field) {
        JsonNode n = payload.get(field);
        if (n == null || !n.isTextual() || n.asText().isBlank()) {
            throw new IllegalArgumentException("Campo '" + field + "' ausente ou inválido");
        }
        return n.asText();
    }

    public int integer(String field) {
        JsonNode n = payload.get(field);
        if (n == null || !n.isIntegralNumber()) {
            throw new IllegalArgumentException("Campo '" + field + "' ausente ou inválido");
        }
        return n.asInt();
    }

    public boolean bool(String field) {
        JsonNode n = payload.get(field);
        if (n == null || !n.isBoolean()) {
            throw new IllegalArgumentException("Campo '" + field + "' ausente ou inválido");
        }
        return n.asBoolean();
    }

    public String optText(String field) {
        JsonNode n = payload.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
