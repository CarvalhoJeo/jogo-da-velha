# Requisitos e rastreabilidade

Cada requisito base tem um componente responsável e uma semana de entrega. Dos 5 opcionais, 2 são compromisso e 3 só entram com a base pronta e testada.

## Requisitos base

| # | Requisito | Como será atendido | Semana |
| --- | --- | --- | --- |
| 1 | Cadastro com persistência segura | `REGISTER` + PBKDF2 + tabela `Users` | 2 a 3 |
| 2 | Autenticação sem expor dados | `LOGIN` sobre TLS, token de sessão, resposta genérica em falha | 2 a 3 |
| 3 | Usuários online | `PresenceService` + broadcast `ONLINE_USERS` | 3 |
| 4 | Criar jogo e convidar | `INVITE` / `INVITE_RECEIVED` | 3 |
| 5 | Aceitar ou recusar convite | `INVITE_REPLY`, expiração em 30 s | 3 |
| 6 | Jogar na sua vez, tabuleiro sincronizado | Fila por partida + `GAME_STATE` para ambos | 4 |
| 7 | Resultado final para ambos | `GAME_OVER` com WIN / LOSS / DRAW e a linha vencedora | 4 |
| 8 | Sair e conceder vitória | `RESIGN` | 4 |
| 9 | Monitorar eventos do servidor | `EventLogger` em arquivo + tabela `ServerEvents` | 2 a 5 |
| 10 | Sem limite de usuários e partidas simultâneas | Virtual thread por conexão e uma `GameSession` por partida, sem limite fixo no código; comprovado no teste de carga | 3 a 5 |

## Opcionais

| Opcional | Situação | Como | Semana |
| --- | --- | --- | --- |
| Reconexão | Escolhido | Heartbeat, estado `PAUSED_DISCONNECTED`, `RECONNECT` reenvia o tabuleiro | 6 |
| Placar | Escolhido | Consulta de vitórias e empates sobre `Games` | 6 |
| Bot | Se sobrar tempo | `BotPlayer` com minimax | 6 |
| Painel admin | Se sobrar tempo | Página web com online, jogos ativos, conexões e erros | 6 |
| Nuvem | Se sobrar tempo | VM Linux com o servidor em `.jar` | 6 |

## Requisitos não funcionais

- Todo tráfego cifrado com TLS 1.3.
- Senhas nunca armazenadas em texto.
- Servidor não cai por mensagem malformada ou cliente desconectado.
- Estado do jogo consistente para os dois jogadores em todos os casos.
