package br.unisinos.velha.client;

import br.unisinos.velha.client.ui.VelhaApp;
import javafx.application.Application;

/**
 * Ponto de entrada do jar do cliente. Fica fora da classe {@link Application}
 * para que o jar único rode com {@code java -jar} sem precisar de --module-path.
 */
public final class ClientLauncher {

    private ClientLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(VelhaApp.class, args);
    }
}
