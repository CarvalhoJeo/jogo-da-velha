package br.unisinos.velha.client.ui;

import br.unisinos.velha.common.protocol.Message;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Tela da partida: tabuleiro 3x3, de quem é a vez e desistência. */
class GameView {

    private final VelhaApp app;
    private final VBox root = new VBox(16);
    private final Label title = new Label();
    private final Label status = new Label();
    private final Label error = new Label();
    private final Button[] cells = new Button[9];
    private final Button resign = new Button("Desistir");
    private final Button back = new Button("Voltar ao lobby");

    private String gameId;
    private String symbol;
    private String opponent;
    private boolean myTurn;
    private boolean over;

    GameView(VelhaApp app) {
        this.app = app;
        title.getStyleClass().add("title");
        status.getStyleClass().add("status");
        error.getStyleClass().add("error");

        GridPane board = new GridPane();
        board.setAlignment(Pos.CENTER);
        for (int i = 0; i < 9; i++) {
            int cell = i;
            Button b = new Button();
            b.getStyleClass().add("cell");
            b.setPrefSize(100, 100);
            b.setFocusTraversable(false);
            b.setOnAction(e -> onCell(cell));
            cells[i] = b;
            board.add(b, i % 3, i / 3);
        }

        resign.getStyleClass().add("danger");
        resign.setOnAction(e -> app.resign(gameId));
        back.getStyleClass().add("primary");
        back.setOnAction(e -> app.backToLobby());
        HBox actions = new HBox(10, resign, back);
        actions.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, status, board, actions, error);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
    }

    void start(String gameId, String symbol, String opponent) {
        this.gameId = gameId;
        this.symbol = symbol;
        this.opponent = opponent;
        this.over = false;
        title.setText("Você (" + symbol + ") vs " + opponent);
        error.setText("");
        resign.setVisible(true);
        back.setVisible(false);
        for (Button b : cells) {
            b.setText("");
            b.getStyleClass().setAll("button", "cell");
        }
        status.setText("Partida iniciada");
    }

    void update(Message m) {
        if (!m.text("gameId").equals(gameId)) {
            return;
        }
        render(m.payload().get("board"));
        String turn = m.optText("turn");
        myTurn = symbol.equals(turn);
        if (!over && turn != null) {
            status.setText(myTurn ? "Sua vez" : "Vez de " + opponent);
        }
        error.setText("");
    }

    void gameOver(Message m) {
        over = true;
        myTurn = false;
        render(m.payload().get("board"));
        JsonNode line = m.payload().get("winningLine");
        if (line != null) {
            for (JsonNode c : line) {
                cells[c.asInt()].getStyleClass().add("cell-win");
            }
        }
        String reason = switch (m.text("reason")) {
            case "RESIGN" -> " por desistência";
            case "TIMEOUT" -> " por abandono";
            default -> "";
        };
        status.setText(switch (m.text("result")) {
            case "WIN" -> "Você venceu" + reason + "!";
            case "LOSS" -> "Você perdeu" + reason + ".";
            default -> "TIMEOUT".equals(m.text("reason")) ? "Partida encerrada sem vencedor." : "Empate!";
        });
        resign.setVisible(false);
        back.setVisible(true);
    }

    void opponentDisconnected(int seconds) {
        if (!over) {
            status.setText(opponent + " caiu. Aguardando reconexão por até " + seconds + " s...");
        }
    }

    void opponentReconnected() {
        if (!over) {
            status.setText(opponent + " voltou. " + (myTurn ? "Sua vez" : "Vez de " + opponent));
        }
    }

    void showReconnecting(int attempt) {
        status.setText("Conexão perdida. Reconectando (tentativa " + attempt + ")...");
    }

    void showError(String text) {
        error.setText(text);
    }

    private void onCell(int cell) {
        if (over) {
            return;
        }
        if (!myTurn) {
            error.setText("Aguarde a sua vez");
            return;
        }
        app.move(gameId, cell); // só a intenção; o tabuleiro muda quando o servidor confirmar
    }

    private void render(JsonNode board) {
        for (int i = 0; i < 9; i++) {
            String s = board.get(i).asText();
            cells[i].setText(s);
            cells[i].getStyleClass().setAll("button", "cell");
            if (!s.isEmpty()) {
                cells[i].getStyleClass().add(s.equals("X") ? "cell-x" : "cell-o");
            }
        }
    }

    Parent root() {
        return root;
    }
}
