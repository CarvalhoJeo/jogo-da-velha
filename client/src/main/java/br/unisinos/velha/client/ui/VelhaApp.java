package br.unisinos.velha.client.ui;

import br.unisinos.velha.client.ClientSettings;
import br.unisinos.velha.client.ServerConnection;
import br.unisinos.velha.common.protocol.ErrorCode;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Cliente JavaFX: telas de login, lobby (online, convites e placar) e partida.
 * O cliente só envia intenções; tudo o que aparece na tela vem do servidor.
 */
public class VelhaApp extends Application implements ServerConnection.Listener {

    private Stage stage;
    private ServerConnection connection;
    private LoginView loginView;
    private LobbyView lobbyView;
    private GameView gameView;
    private String username;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        ClientSettings settings = ClientSettings.parse(getParameters().getRaw());
        connection = new ServerConnection(settings, this);
        loginView = new LoginView(this, settings.host(), settings.port());
        lobbyView = new LobbyView(this);
        gameView = new GameView(this);

        stage.setTitle("Jogo da Velha em Rede" + (settings.tls() ? "" : " (SEM TLS)"));
        stage.setScene(new Scene(loginView.root(), 720, 520));
        stage.getScene().getStylesheets().add(Styles.CSS);
        stage.setOnCloseRequest(e -> connection.close());
        stage.show();
    }

    @Override
    public void stop() {
        connection.close();
    }

    // ---- ações vindas das telas ----

    void connectAndSend(String host, int port, String type, String user, String password) {
        loginView.setBusy(true);
        Thread.ofVirtual().start(() -> {
            try {
                connection.connect(host, port);
                connection.request(type, Json.object().put("username", user).put("password", password));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loginView.setBusy(false);
                    loginView.showError("Não foi possível conectar: " + e.getMessage());
                });
            }
        });
    }

    void invite(String target) {
        connection.request(MessageType.INVITE, Json.object().put("targetUser", target));
    }

    void replyInvite(String inviteId, boolean accept) {
        connection.request(MessageType.INVITE_REPLY, Json.object().put("inviteId", inviteId).put("accept", accept));
    }

    void move(String gameId, int cell) {
        connection.request(MessageType.MOVE, Json.object().put("gameId", gameId).put("cell", cell));
    }

    void resign(String gameId) {
        connection.request(MessageType.RESIGN, Json.object().put("gameId", gameId));
    }

    void requestLeaderboard() {
        connection.request(MessageType.LEADERBOARD);
    }

    void logout() {
        connection.logout();
        username = null;
        show(loginView.root());
    }

    void backToLobby() {
        show(lobbyView.root());
        requestLeaderboard();
    }

    String username() {
        return username;
    }

    // ---- eventos da rede (threads de rede -> thread do JavaFX) ----

    @Override
    public void onMessage(Message m) {
        Platform.runLater(() -> handle(m));
    }

    @Override
    public void onConnectionLost(String reason) {
        Platform.runLater(() -> {
            username = null;
            lobbyView.clearInvites();
            show(loginView.root());
            loginView.setBusy(false);
            loginView.showError(reason);
        });
    }

    @Override
    public void onReconnecting(int attempt) {
        Platform.runLater(() -> gameView.showReconnecting(attempt));
    }

    private void handle(Message m) {
        switch (m.type()) {
            case MessageType.REGISTER_OK -> {
                loginView.setBusy(false);
                loginView.showInfo("Cadastro concluído. Agora é só entrar.");
            }
            case MessageType.AUTH_FAIL -> {
                loginView.setBusy(false);
                loginView.showError(m.optText("reason"));
            }
            case MessageType.AUTH_OK -> {
                username = m.text("username");
                loginView.setBusy(false);
                loginView.clearPassword();
                lobbyView.setUser(username);
                if (!m.payload().path("reconnected").asBoolean()) {
                    show(lobbyView.root());
                    requestLeaderboard();
                }
            }
            case MessageType.ONLINE_USERS -> lobbyView.updateOnline(m.payload().get("users"));
            case MessageType.INVITE_SENT -> lobbyView.status("Convite enviado para " + m.text("to") + ". Aguardando resposta...");
            case MessageType.INVITE_RECEIVED -> lobbyView.showInvite(m.text("inviteId"), m.text("from"),
                    m.payload().path("expiresInSeconds").asInt(30));
            case MessageType.INVITE_RESULT -> lobbyView.inviteResult(m);
            case MessageType.GAME_START -> {
                lobbyView.clearInvites();
                gameView.start(m.text("gameId"), m.text("symbol"), m.text("opponent"));
                show(gameView.root());
            }
            case MessageType.GAME_STATE -> gameView.update(m);
            case MessageType.GAME_OVER -> gameView.gameOver(m);
            case MessageType.OPPONENT_DISCONNECTED -> gameView.opponentDisconnected(m.payload().path("timeoutSeconds").asInt());
            case MessageType.OPPONENT_RECONNECTED -> gameView.opponentReconnected();
            case MessageType.LEADERBOARD_DATA -> lobbyView.updateLeaderboard(m.payload().get("rows"));
            case MessageType.ERROR -> handleError(m);
            default -> { }
        }
    }

    private void handleError(Message m) {
        String code = m.optText("code");
        String text = m.optText("message");
        if (ErrorCode.RECONNECT_FAILED.equals(code) || ErrorCode.SESSION_REPLACED.equals(code)) {
            connection.forgetSession();
            onConnectionLost(text);
            return;
        }
        if (stage.getScene().getRoot() == gameView.root()) {
            gameView.showError(text);
        } else if (stage.getScene().getRoot() == lobbyView.root()) {
            lobbyView.status(text);
        } else {
            loginView.setBusy(false);
            loginView.showError(text);
        }
    }

    private void show(Parent root) {
        stage.getScene().setRoot(root);
    }
}
