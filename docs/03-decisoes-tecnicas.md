# Decisões técnicas

A stack usa Java, uma das linguagens preferidas no guia, com sockets TCP puros, para que a programação de sockets e o tráfego no Wireshark fiquem visíveis em vez de escondidos atrás de um framework.

| Camada | Escolha | Por quê |
| --- | --- | --- |
| Linguagem | Java 21 (LTS) | Preferida no guia; virtual threads simplificam uma thread por conexão |
| Build | Maven, com módulos `server`, `client` e `common` | `common` guarda o protocolo e as regras, compartilhados pelos dois lados |
| Transporte | `SSLServerSocket` / `SSLSocket` (JSSE) | Sockets explícitos na camada 4, com TLS integrado |
| Segurança do canal | TLS 1.3 com certificado autoassinado gerado pelo `keytool` | Confidencialidade e integridade, verificável no Wireshark |
| Enquadramento | `DataOutputStream.writeInt(tamanho)` + JSON UTF-8 | Resolve o problema de TCP ser fluxo de bytes, sem mensagens |
| Serialização | Jackson | Biblioteca padrão de mercado para JSON em Java |
| Senhas | PBKDF2 (`PBKDF2WithHmacSHA256`, 210k iterações, salt por usuário) | Nativo no JDK, hash lento e com salt |
| Banco | SQLite via JDBC (`sqlite-jdbc`) | Zero instalação, fácil de entregar e demonstrar |
| Barramento de eventos | `BlockingQueue` por partida + um consumidor por fila | Fila de ações e turnos, conforme o conceito de event bus do curso |
| Cliente | JavaFX | Interface gráfica simples, nativa do ecossistema Java |
| Painel admin (se sobrar tempo) | `com.sun.net.httpserver.HttpServer` | Embutido no JDK, sem framework extra |
| Logs | SLF4J + Logback, mais a tabela `ServerEvents` | Eventos do servidor em arquivo e no banco |
| Testes | JUnit 5 | Padrão para testes em Java |
| Versionamento | Git + GitHub, uma branch e um PR por feature | Exigido no guia; ver [git-workflow.md](../guidelines/git-workflow.md) |
| Build reproduzível | Maven Wrapper (`./mvnw`) | Quem clona não precisa instalar o Maven |
| Distribuição | `maven-shade-plugin`: `velha-server.jar` e `velha-client.jar` | Um `java -jar` por componente na demo; o jar do cliente leva o JavaFX do sistema em que foi gerado |
| Cliente de console | `ConsoleClient` e `LoadTestMain` no módulo `client` | Testes manuais, depuração do protocolo e teste de carga entre máquinas |

## Decisões tomadas durante a implementação

- **Mensagens novas:** `REGISTER_OK`, `LOGOUT`, `INVITE_SENT`, `INVITE_RESULT`, `OPPONENT_DISCONNECTED`, `OPPONENT_RECONNECTED` e `LEADERBOARD_DATA` foram acrescentadas para que todo pedido tenha resposta e para que os dois lados saibam de convites recusados, expirados e de quedas. Ver [05-protocolo.md](05-protocolo.md).
- **Token de sessão:** vale só para a conexão em que foi emitido. Se a conexão cai **com partida em andamento**, o token passa a valer para `RECONNECT` por 60 s; sem partida, é descartado na hora. `LOGOUT` e um novo login invalidam o token anterior.
- **Novo login retoma a partida:** se o jogador fecha o app e entra de novo dentro dos 60 s, a partida volta como em um `RECONNECT`. Um segundo login do mesmo usuário derruba a sessão antiga (`SESSION_REPLACED`).
- **Queda dos dois jogadores:** se ninguém voltar em 60 s, a partida termina sem vencedor (`TIMEOUT`, `WinnerId` nulo).
- **Verificação do certificado:** o cliente confia apenas no certificado do servidor, carregado num truststore próprio (fixação de certificado). Não há verificação de nome de host, para que a demo funcione por IP; mesmo assim, um MitM com outro certificado é recusado no handshake.
- **Heartbeat:** o cliente envia `PING` a cada 10 s; o servidor responde `PONG`. Os dois lados consideram a conexão perdida após 30 s sem receber nada.
- **Modo `--sem-tls`:** existe só para a captura de comparação no Wireshark, porque o JSSE não exporta chaves de sessão.
- **Bot, painel admin e nuvem:** não implementados nesta versão (opcionais "se sobrar tempo").

## Por que não MQTT

O guia aceita MQTT com broker como opcional, mas pede justificativa se não for usado.

- Sockets TCP diretos deixam o protocolo, o enquadramento e o TLS sob nosso controle e visíveis no Wireshark, que é o foco do curso.
- Um broker esconderia parte disso e acrescentaria mais um componente para instalar, proteger e monitorar.
- O roteamento por partida que o broker faria fica no `MessageRouter`, com as filas de cada `GameSession`.

## Por que sockets e não um framework web

- O guia pede comunicação por sockets sobre TCP/IP.
- WebSocket ou HTTP esconderiam o enquadramento e o ciclo de vida da conexão, que são justamente o que a disciplina quer ver.
