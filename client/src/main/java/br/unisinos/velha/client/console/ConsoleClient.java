package br.unisinos.velha.client.console;

import br.unisinos.velha.client.ClientSettings;
import br.unisinos.velha.common.net.ProtocolClient;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Cliente de console, útil para testes manuais, depuração do protocolo e
 * para demonstrar o servidor sem interface gráfica.
 *
 * <pre>
 * java -cp client/target/velha-client.jar br.unisinos.velha.client.console.ConsoleClient --host localhost --port 5555
 * </pre>
 */
public final class ConsoleClient {

    private static final String HELP = """
            Comandos:
              cadastrar <usuario> <senha>   login <usuario> <senha>   sair
              convidar <usuario>            aceitar <id>              recusar <id>
              jogar <casa 0-8>              desistir                  placar
              ajuda                         fim
            """;

    private static volatile String gameId;
    private static volatile String lastInvite;

    private ConsoleClient() {
    }

    public static void main(String[] args) throws Exception {
        ClientSettings s = ClientSettings.parse(Arrays.asList(args));
        ProtocolClient c = ProtocolClient.connect(s.host(), s.port(), s.sslContext());
        c.setReadTimeout(Duration.ofSeconds(30));
        c.startHeartbeat(Duration.ofSeconds(10));
        c.setListener(m -> print(c, m));
        c.setOnDisconnect(() -> {
            System.out.println("\n[conexão encerrada]");
            System.exit(0);
        });
        System.out.println("Conectado a " + s.host() + ":" + s.port() + (s.tls() ? " (TLS 1.3)" : " (SEM TLS)"));
        System.out.print(HELP);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            List<String> p = Arrays.stream(line.trim().split("\\s+")).filter(x -> !x.isEmpty()).toList();
            if (p.isEmpty()) {
                continue;
            }
            try {
                switch (p.get(0)) {
                    case "cadastrar" -> c.request(MessageType.REGISTER,
                            Json.object().put("username", p.get(1)).put("password", p.get(2)));
                    case "login" -> c.request(MessageType.LOGIN,
                            Json.object().put("username", p.get(1)).put("password", p.get(2)));
                    case "sair" -> c.request(MessageType.LOGOUT);
                    case "convidar" -> c.request(MessageType.INVITE, Json.object().put("targetUser", p.get(1)));
                    case "aceitar", "recusar" -> c.request(MessageType.INVITE_REPLY, Json.object()
                            .put("inviteId", p.size() > 1 ? p.get(1) : lastInvite)
                            .put("accept", p.get(0).equals("aceitar")));
                    case "jogar" -> c.request(MessageType.MOVE,
                            Json.object().put("gameId", gameId).put("cell", Integer.parseInt(p.get(1))));
                    case "desistir" -> c.request(MessageType.RESIGN, Json.object().put("gameId", gameId));
                    case "placar" -> c.request(MessageType.LEADERBOARD);
                    case "ajuda" -> System.out.print(HELP);
                    case "fim" -> {
                        c.close();
                        return;
                    }
                    default -> System.out.println("Comando desconhecido. Digite 'ajuda'.");
                }
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                System.out.println("Argumentos inválidos. Digite 'ajuda'.");
            }
        }
        c.close();
    }

    private static void print(ProtocolClient c, Message m) {
        JsonNode p = m.payload();
        switch (m.type()) {
            case MessageType.AUTH_OK -> {
                c.setToken(m.text("token"));
                System.out.println("Logado como " + m.text("username"));
            }
            case MessageType.REGISTER_OK -> System.out.println("Cadastro concluído: " + m.text("username"));
            case MessageType.AUTH_FAIL -> System.out.println("Falha: " + m.optText("reason"));
            case MessageType.ONLINE_USERS -> {
                StringBuilder sb = new StringBuilder("Online:");
                p.get("users").forEach(u -> sb.append(' ').append(u.get("username").asText())
                        .append(u.get("inGame").asBoolean() ? "(jogando)" : ""));
                System.out.println(sb);
            }
            case MessageType.INVITE_RECEIVED -> {
                lastInvite = m.text("inviteId");
                System.out.println("Convite " + lastInvite + " de " + m.text("from") + " (aceitar/recusar)");
            }
            case MessageType.GAME_START -> {
                gameId = m.text("gameId");
                System.out.println("Partida " + gameId + ": você é " + m.text("symbol") + " contra " + m.text("opponent"));
            }
            case MessageType.GAME_STATE -> {
                JsonNode b = p.get("board");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 9; i++) {
                    String v = b.get(i).asText();
                    sb.append(' ').append(v.isEmpty() ? String.valueOf(i) : v).append(i % 3 == 2 ? "\n" : " |");
                }
                System.out.print(sb);
                if (p.hasNonNull("turn")) {
                    System.out.println("Vez de " + p.get("turn").asText());
                }
            }
            case MessageType.GAME_OVER -> {
                System.out.println("Fim de jogo: " + m.text("result") + " (" + m.text("reason") + ")");
                gameId = null;
            }
            case MessageType.LEADERBOARD_DATA -> {
                System.out.println("Placar (vitórias / empates / derrotas):");
                p.get("rows").forEach(r -> System.out.printf("  %-20s %3d %3d %3d%n", r.get("username").asText(),
                        r.get("wins").asInt(), r.get("draws").asInt(), r.get("losses").asInt()));
            }
            case MessageType.ERROR -> System.out.println("Erro " + m.optText("code") + ": " + m.optText("message"));
            case MessageType.PONG -> { }
            default -> System.out.println(m.type() + " " + p);
        }
    }
}
