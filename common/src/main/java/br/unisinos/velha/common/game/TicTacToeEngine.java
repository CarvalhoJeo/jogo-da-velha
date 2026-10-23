package br.unisinos.velha.common.game;

import java.util.Arrays;

/**
 * Regras puras do Jogo da Velha, sem rede. Casas numeradas de 0 a 8, da
 * esquerda para a direita e de cima para baixo. X sempre começa.
 *
 * Não é thread-safe: no servidor, cada instância pertence a uma única
 * {@code GameSession}, que processa as jogadas em ordem.
 */
public class TicTacToeEngine {

    public static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // linhas
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // colunas
            {0, 4, 8}, {2, 4, 6}             // diagonais
    };

    public enum MoveResult { OK, GAME_OVER, NOT_YOUR_TURN, OUT_OF_RANGE, OCCUPIED }

    private final Mark[] board = new Mark[9];
    private Mark turn = Mark.X;
    private int moveCount;
    private int lastMove = -1;
    private Mark winner = Mark.EMPTY;
    private int[] winningLine;

    public TicTacToeEngine() {
        Arrays.fill(board, Mark.EMPTY);
    }

    /** Aplica a jogada de {@code player} na casa {@code cell}, se for válida. */
    public MoveResult play(Mark player, int cell) {
        if (isOver()) {
            return MoveResult.GAME_OVER;
        }
        if (player != turn) {
            return MoveResult.NOT_YOUR_TURN;
        }
        if (cell < 0 || cell > 8) {
            return MoveResult.OUT_OF_RANGE;
        }
        if (board[cell] != Mark.EMPTY) {
            return MoveResult.OCCUPIED;
        }
        board[cell] = player;
        moveCount++;
        lastMove = cell;
        int[] line = findWinningLine();
        if (line != null) {
            winner = player;
            winningLine = line;
        } else {
            turn = turn.opponent();
        }
        return MoveResult.OK;
    }

    private int[] findWinningLine() {
        for (int[] l : LINES) {
            Mark a = board[l[0]];
            if (a != Mark.EMPTY && a == board[l[1]] && a == board[l[2]]) {
                return l.clone();
            }
        }
        return null;
    }

    /** Vencedor, ou {@link Mark#EMPTY} se ainda não há. */
    public Mark winner() {
        return winner;
    }

    public int[] winningLine() {
        return winningLine == null ? null : winningLine.clone();
    }

    public boolean isDraw() {
        return winner == Mark.EMPTY && moveCount == 9;
    }

    public boolean isOver() {
        return winner != Mark.EMPTY || moveCount == 9;
    }

    public Mark turn() {
        return turn;
    }

    public int moveCount() {
        return moveCount;
    }

    public int lastMove() {
        return lastMove;
    }

    public Mark at(int cell) {
        return board[cell];
    }

    public Mark[] board() {
        return board.clone();
    }
}
