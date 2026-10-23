package br.unisinos.velha.common.game;

public enum Mark {
    X, O, EMPTY;

    public Mark opponent() {
        return switch (this) {
            case X -> O;
            case O -> X;
            case EMPTY -> EMPTY;
        };
    }

    /** Representação no protocolo: "X", "O" ou "" para casa vazia. */
    public String symbol() {
        return this == EMPTY ? "" : name();
    }

    public static Mark fromSymbol(String s) {
        return s == null || s.isEmpty() ? EMPTY : Mark.valueOf(s);
    }
}
