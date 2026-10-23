package br.unisinos.velha.client.ui;

import br.unisinos.velha.common.protocol.Message;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/** Lobby: usuários online, convites e placar. */
class LobbyView {

    record OnlineUser(String username, boolean inGame) {
    }

    record Row(String username, int wins, int draws, int losses, int games) {
    }

    private final VelhaApp app;
    private final BorderPane root = new BorderPane();
    private final Label hello = new Label();
    private final Label status = new Label("Escolha um jogador e clique em Convidar.");
    private final ListView<OnlineUser> online = new ListView<>();
    private final TableView<Row> leaderboard = new TableView<>();
    private final Map<String, Alert> openInvites = new HashMap<>();

    LobbyView(VelhaApp app) {
        this.app = app;

        hello.getStyleClass().add("title");
        Button logout = new Button("Sair");
        logout.setOnAction(e -> app.logout());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, hello, spacer, logout);
        top.setAlignment(Pos.CENTER_LEFT);

        online.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(OnlineUser u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.username() + (u.inGame() ? "  (em partida)" : ""));
            }
        });
        online.setPlaceholder(new Label("Ninguém mais online"));
        Button invite = new Button("Convidar");
        invite.getStyleClass().add("primary");
        invite.setMaxWidth(Double.MAX_VALUE);
        invite.disableProperty().bind(online.getSelectionModel().selectedItemProperty().isNull());
        invite.setOnAction(e -> {
            OnlineUser u = online.getSelectionModel().getSelectedItem();
            if (u != null) {
                app.invite(u.username());
            }
        });
        online.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && online.getSelectionModel().getSelectedItem() != null) {
                app.invite(online.getSelectionModel().getSelectedItem().username());
            }
        });
        Label onlineTitle = new Label("Online");
        onlineTitle.getStyleClass().add("subtitle");
        VBox left = new VBox(10, onlineTitle, online, invite);
        left.getStyleClass().add("card");
        VBox.setVgrow(online, Priority.ALWAYS);

        leaderboard.getColumns().add(column("Jogador", r -> new SimpleStringProperty(r.username())));
        leaderboard.getColumns().add(column("Vitórias", r -> new SimpleIntegerProperty(r.wins()).asObject()));
        leaderboard.getColumns().add(column("Empates", r -> new SimpleIntegerProperty(r.draws()).asObject()));
        leaderboard.getColumns().add(column("Derrotas", r -> new SimpleIntegerProperty(r.losses()).asObject()));
        leaderboard.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        leaderboard.setPlaceholder(new Label("Nenhuma partida terminada ainda"));
        Button refresh = new Button("Atualizar");
        refresh.setOnAction(e -> app.requestLeaderboard());
        Label boardTitle = new Label("Placar");
        boardTitle.getStyleClass().add("subtitle");
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        VBox right = new VBox(10, new HBox(10, boardTitle, spacer2, refresh), leaderboard);
        right.getStyleClass().add("card");
        VBox.setVgrow(leaderboard, Priority.ALWAYS);

        HBox center = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(260);
        right.setPrefWidth(400);

        status.setWrapText(true);
        root.setTop(top);
        root.setCenter(center);
        root.setBottom(status);
        BorderPane.setMargin(center, new Insets(16, 0, 16, 0));
        root.setPadding(new Insets(20));
    }

    private static <T> TableColumn<Row, T> column(String title,
                                                  java.util.function.Function<Row, javafx.beans.value.ObservableValue<T>> f) {
        TableColumn<Row, T> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> f.apply(cd.getValue()));
        return c;
    }

    void setUser(String username) {
        hello.setText("Olá, " + username);
    }

    void updateOnline(JsonNode users) {
        String selected = online.getSelectionModel().getSelectedItem() == null ? null
                : online.getSelectionModel().getSelectedItem().username();
        online.getItems().clear();
        for (JsonNode u : users) {
            String name = u.get("username").asText();
            if (!name.equalsIgnoreCase(app.username())) {
                online.getItems().add(new OnlineUser(name, u.get("inGame").asBoolean()));
            }
        }
        online.getItems().stream().filter(u -> u.username().equals(selected)).findFirst()
                .ifPresent(u -> online.getSelectionModel().select(u));
    }

    void updateLeaderboard(JsonNode rows) {
        leaderboard.getItems().clear();
        for (JsonNode r : rows) {
            leaderboard.getItems().add(new Row(r.get("username").asText(), r.get("wins").asInt(),
                    r.get("draws").asInt(), r.get("losses").asInt(), r.get("games").asInt()));
        }
    }

    void showInvite(String inviteId, String from, int seconds) {
        ButtonType accept = new ButtonType("Aceitar", ButtonBar.ButtonData.OK_DONE);
        ButtonType decline = new ButtonType("Recusar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, from + " convidou você para uma partida.\n"
                + "O convite expira em " + seconds + " s.", accept, decline);
        a.setTitle("Convite");
        a.setHeaderText("Novo convite");
        openInvites.put(inviteId, a);
        a.setOnHidden(e -> {
            // fechado pelo servidor (expirou/cancelado) não responde nada
            if (openInvites.remove(inviteId) != null) {
                boolean ok = a.getResult() == accept;
                app.replyInvite(inviteId, ok);
            }
        });
        a.show();
    }

    void inviteResult(Message m) {
        String id = m.text("inviteId");
        Alert a = openInvites.remove(id);
        if (a != null) {
            a.close();
        }
        String st = m.text("status");
        boolean mine = m.text("from").equalsIgnoreCase(app.username());
        String other = mine ? m.text("to") : m.text("from");
        switch (st) {
            case "DECLINED" -> status(other + " recusou o convite.");
            case "EXPIRED" -> status(mine ? "Convite para " + other + " expirou." : "Convite de " + other + " expirou.");
            case "CANCELLED" -> status("Convite com " + other + " foi cancelado.");
            default -> status("Convite: " + st);
        }
    }

    void clearInvites() {
        Map<String, Alert> copy = new HashMap<>(openInvites);
        openInvites.clear();
        copy.values().forEach(Alert::close);
    }

    void status(String text) {
        status.setText(text);
    }

    Parent root() {
        return root;
    }
}
