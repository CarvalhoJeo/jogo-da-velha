package br.unisinos.velha.common.game;

import br.unisinos.velha.common.game.TicTacToeEngine.MoveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TicTacToeEngineTest {

    static Stream<int[]> allLines() {
        return Arrays.stream(TicTacToeEngine.LINES);
    }

    /** Req. 7: X vence em cada uma das 8 linhas; O joga em casas fora da linha. */
    @ParameterizedTest
    @MethodSource("allLines")
    void xWinsOnEveryLine(int[] line) {
        TicTacToeEngine e = new TicTacToeEngine();
        List<Integer> others = new ArrayList<>(IntStream.range(0, 9).boxed().toList());
        for (int c : line) {
            others.remove(Integer.valueOf(c));
        }
        // escolhe 2 casas para O que não formem linha com nada que importe
        for (int i = 0; i < 3; i++) {
            assertEquals(MoveResult.OK, e.play(Mark.X, line[i]));
            if (i < 2) {
                assertEquals(MoveResult.OK, e.play(Mark.O, others.get(i)));
            }
        }
        assertEquals(Mark.X, e.winner());
        assertArrayEquals(line, e.winningLine());
        assertTrue(e.isOver());
        assertFalse(e.isDraw());
    }

    @Test
    void oCanWinToo() {
        TicTacToeEngine e = new TicTacToeEngine();
        play(e, 0, 3, 1, 4, 8, 5);
        assertEquals(Mark.O, e.winner());
        assertArrayEquals(new int[]{3, 4, 5}, e.winningLine());
    }

    /** Req. 7: tabuleiro cheio sem 3 em linha é empate. */
    @Test
    void fullBoardWithoutLineIsDraw() {
        TicTacToeEngine e = new TicTacToeEngine();
        // X O X / X O O / O X X
        play(e, 0, 1, 2, 4, 3, 5, 7, 6, 8);
        assertTrue(e.isDraw());
        assertTrue(e.isOver());
        assertEquals(Mark.EMPTY, e.winner());
        assertNull(e.winningLine());
    }

    @Test
    void winOnLastMoveIsNotDraw() {
        TicTacToeEngine e = new TicTacToeEngine();
        // X vence com a 9a jogada na diagonal 0-4-8
        play(e, 0, 1, 2, 5, 4, 6, 3, 7, 8);
        assertEquals(Mark.X, e.winner());
        assertFalse(e.isDraw());
    }

    /** Req. 6: casa ocupada, fora de 0..8 e fora da vez são rejeitadas sem mudar o tabuleiro. */
    @Test
    void rejectsInvalidMoves() {
        TicTacToeEngine e = new TicTacToeEngine();
        assertEquals(MoveResult.NOT_YOUR_TURN, e.play(Mark.O, 0));
        assertEquals(MoveResult.OK, e.play(Mark.X, 4));
        assertEquals(MoveResult.OCCUPIED, e.play(Mark.O, 4));
        assertEquals(MoveResult.OUT_OF_RANGE, e.play(Mark.O, 9));
        assertEquals(MoveResult.OUT_OF_RANGE, e.play(Mark.O, -1));
        assertEquals(MoveResult.NOT_YOUR_TURN, e.play(Mark.X, 0));
        assertEquals(1, e.moveCount());
        assertEquals(Mark.O, e.turn());
        assertEquals(Mark.X, e.at(4));
    }

    @Test
    void rejectsMovesAfterGameOver() {
        TicTacToeEngine e = new TicTacToeEngine();
        play(e, 0, 3, 1, 4, 2);
        assertEquals(MoveResult.GAME_OVER, e.play(Mark.O, 8));
    }

    @Test
    void xStartsAndTurnsAlternate() {
        TicTacToeEngine e = new TicTacToeEngine();
        assertEquals(Mark.X, e.turn());
        e.play(Mark.X, 0);
        assertEquals(Mark.O, e.turn());
        assertEquals(0, e.lastMove());
    }

    private static void play(TicTacToeEngine e, int... cells) {
        Mark m = Mark.X;
        for (int c : cells) {
            assertEquals(MoveResult.OK, e.play(m, c), "jogada em " + c);
            m = m.opponent();
        }
    }
}
