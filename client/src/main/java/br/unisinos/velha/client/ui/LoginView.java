package br.unisinos.velha.client.ui;

import br.unisinos.velha.common.protocol.MessageType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Tela de login e cadastro. */
class LoginView {

    private final VelhaApp app;
    private final StackPane root = new StackPane();
    private final TextField host = new TextField();
    private final TextField port = new TextField();
    private final TextField user = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label message = new Label();
    private final Button login = new Button("Entrar");
    private final Button register = new Button("Cadastrar");

    LoginView(VelhaApp app, String defaultHost, int defaultPort) {
        this.app = app;
        host.setText(defaultHost);
        port.setText(String.valueOf(defaultPort));
        user.setPromptText("3 a 20 letras, números ou _");
        password.setPromptText("mínimo 6 caracteres");

        Label title = new Label("Jogo da Velha");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Entre ou crie sua conta");
        subtitle.getStyleClass().add("subtitle");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Servidor"), host);
        form.addRow(1, new Label("Porta"), port);
        form.addRow(2, new Label("Usuário"), user);
        form.addRow(3, new Label("Senha"), password);

        login.getStyleClass().add("primary");
        login.setDefaultButton(true);
        login.setOnAction(e -> submit(MessageType.LOGIN));
        register.setOnAction(e -> submit(MessageType.REGISTER));
        HBox buttons = new HBox(10, login, register);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        message.setWrapText(true);
        message.setMaxWidth(320);

        VBox card = new VBox(14, title, subtitle, form, buttons, message);
        card.getStyleClass().add("card");
        card.setMaxWidth(380);
        card.setMaxHeight(VBox.USE_PREF_SIZE);
        root.getChildren().add(card);
        root.setPadding(new Insets(20));
    }

    private void submit(String type) {
        message.setText("");
        int p;
        try {
            p = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            showError("Porta inválida");
            return;
        }
        if (user.getText().isBlank() || password.getText().isEmpty()) {
            showError("Preencha usuário e senha");
            return;
        }
        app.connectAndSend(host.getText().trim(), p, type, user.getText().trim(), password.getText());
    }

    void setBusy(boolean busy) {
        login.setDisable(busy);
        register.setDisable(busy);
    }

    void showError(String text) {
        message.getStyleClass().setAll("label", "error");
        message.setText(text);
    }

    void showInfo(String text) {
        message.getStyleClass().setAll("label", "info");
        message.setText(text);
    }

    void clearPassword() {
        password.clear();
        message.setText("");
    }

    Parent root() {
        return root;
    }
}
