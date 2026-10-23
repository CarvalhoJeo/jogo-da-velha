# Monitoramento e depuração

O servidor registra todo evento relevante em arquivo (Logback) e na tabela `ServerEvents`, cobrindo o requisito 9 do guia.

## Eventos registrados

| Categoria | Eventos |
| --- | --- |
| Jogadores | Conexão, cadastro, login, logout, desconexão |
| Partidas | Convite, início e fim (com participantes e motivo), desistência, timeout |
| Erros | Mensagem inválida ou malformada, exceções, falhas de rede, tentativas de login bloqueadas |

Cada registro tem horário, nível (INFO / WARN / ERROR), tipo, usuário, partida e detalhes em JSON.

Tipos gravados em `ServerEvents` (classe `EventLogger`):

| Nível | Tipos |
| --- | --- |
| INFO | `SERVER_STARTED`, `SERVER_STOPPED`, `CONNECTION_OPENED`, `CONNECTION_CLOSED`, `USER_REGISTERED`, `LOGIN`, `LOGOUT`, `DISCONNECT`, `RECONNECT`, `INVITE_SENT`, `INVITE_ACCEPTED`, `INVITE_DECLINED`, `INVITE_EXPIRED`, `GAME_STARTED`, `MOVE`, `RESIGN`, `GAME_RESUMED`, `GAME_ENDED` |
| WARN | `LOGIN_FAILED`, `LOGIN_BLOCKED`, `REGISTER_FAILED`, `RECONNECT_FAILED`, `GAME_PAUSED`, `MALFORMED_MESSAGE`, `UNKNOWN_TYPE`, `FRAME_TOO_LARGE`, `RATE_LIMITED`, `TIMEOUT`, `TLS_HANDSHAKE_FAILED`, `NETWORK_ERROR` |
| ERROR | `ERROR` (exceção inesperada, com a mensagem nos detalhes) |

Exemplo de consulta: `sqlite3 velha.db "SELECT Timestamp, Level, Type, Details FROM ServerEvents ORDER BY Id DESC LIMIT 20"`.

## Ferramentas de depuração

- **Logs:** `logs/server.log` com rotação diária.
- **Banco:** consultas em `ServerEvents` para reconstruir o que aconteceu.
- **Wireshark:** filtro `tcp.port == <porta>` para ver o handshake TLS e o tráfego cifrado.
- **Modo sem TLS (só em teste):** o JDK não exporta as chaves de sessão TLS (não há `SSLKEYLOGFILE` no JSSE), então, para ver o JSON no Wireshark, servidor e cliente aceitam `--sem-tls`. É o mesmo modo usado na captura "antes do TLS". O servidor avisa no log quando sobe assim.
- **Cliente de console:** `ConsoleClient` mostra cada mensagem recebida, útil para depurar o protocolo sem a interface gráfica.
- **Painel admin (opcional):** usuários online, jogos ativos, conexões e erros em tempo real.
