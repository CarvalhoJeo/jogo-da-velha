package br.unisinos.velha.common.protocol;

/** Códigos enviados no payload de {@link MessageType#ERROR}. */
public final class ErrorCode {

    public static final String MALFORMED = "MALFORMED";
    public static final String FRAME_TOO_LARGE = "FRAME_TOO_LARGE";
    public static final String UNKNOWN_TYPE = "UNKNOWN_TYPE";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String NOT_AUTHENTICATED = "NOT_AUTHENTICATED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String USER_OFFLINE = "USER_OFFLINE";
    public static final String USER_BUSY = "USER_BUSY";
    public static final String INVALID_INVITE = "INVALID_INVITE";
    public static final String GAME_NOT_FOUND = "GAME_NOT_FOUND";
    public static final String NOT_YOUR_TURN = "NOT_YOUR_TURN";
    public static final String INVALID_MOVE = "INVALID_MOVE";
    public static final String GAME_PAUSED = "GAME_PAUSED";
    public static final String RECONNECT_FAILED = "RECONNECT_FAILED";
    public static final String SESSION_REPLACED = "SESSION_REPLACED";
    public static final String INTERNAL = "INTERNAL";

    private ErrorCode() {
    }
}
