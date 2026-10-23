package br.unisinos.velha.common.protocol;

/** Tipos de mensagem do protocolo. A tabela completa está em docs/05-protocolo.md. */
public final class MessageType {

    // Cliente -> servidor
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String INVITE = "INVITE";
    public static final String INVITE_REPLY = "INVITE_REPLY";
    public static final String MOVE = "MOVE";
    public static final String RESIGN = "RESIGN";
    public static final String RECONNECT = "RECONNECT";
    public static final String LEADERBOARD = "LEADERBOARD";

    // Servidor -> cliente
    public static final String REGISTER_OK = "REGISTER_OK";
    public static final String AUTH_OK = "AUTH_OK";
    public static final String AUTH_FAIL = "AUTH_FAIL";
    public static final String ONLINE_USERS = "ONLINE_USERS";
    public static final String INVITE_SENT = "INVITE_SENT";
    public static final String INVITE_RECEIVED = "INVITE_RECEIVED";
    public static final String INVITE_RESULT = "INVITE_RESULT";
    public static final String GAME_START = "GAME_START";
    public static final String GAME_STATE = "GAME_STATE";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String OPPONENT_DISCONNECTED = "OPPONENT_DISCONNECTED";
    public static final String OPPONENT_RECONNECTED = "OPPONENT_RECONNECTED";
    public static final String LEADERBOARD_DATA = "LEADERBOARD_DATA";
    public static final String ERROR = "ERROR";

    // Ambos
    public static final String PING = "PING";
    public static final String PONG = "PONG";

    private MessageType() {
    }
}
