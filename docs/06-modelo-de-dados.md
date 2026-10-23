# Modelo de dados

Quatro tabelas cobrem cadastro, histórico, placar e monitoramento. O placar é uma consulta sobre `Games`, sem tabela própria, para nunca ficar inconsistente.

| Tabela | Colunas principais |
| --- | --- |
| `Users` | Id, Username (único), PasswordHash, Salt, Iterations, CreatedAt, LastLoginAt |
| `Games` | Id, PlayerXId, PlayerOId (nulo se bot), BotLevel, Status (IN_PROGRESS / PAUSED_DISCONNECTED / FINISHED / ABORTED), WinnerId, EndReason (THREE_IN_ROW / DRAW / RESIGN / TIMEOUT / SERVER_RESTART), StartedAt, EndedAt |
| `Moves` | Id, GameId, PlayerId, Cell (0 a 8), MoveNumber, CreatedAt |
| `ServerEvents` | Id, Timestamp, Level (INFO / WARN / ERROR), Type (USER_REGISTERED, LOGIN, GAME_STARTED, GAME_ENDED, DISCONNECT, ERROR...), UserId, GameId, Details (JSON) |

A tabela `Moves` também permite reconstruir uma partida após queda do servidor e mostrar o replay na apresentação.

Na inicialização, o servidor marca como `ABORTED` (motivo `SERVER_RESTART`) as partidas que estavam em andamento quando ele caiu; as jogadas continuam em `Moves`. O identificador da partida no protocolo é `g-<Id>`.

O esquema é criado por `Database.migrate()` (módulo `server`). `Username` usa `COLLATE NOCASE`, então "Ana" e "ana" são o mesmo usuário.

## Consulta do placar

```sql
SELECT u.Username,
       SUM(CASE WHEN g.WinnerId = u.Id THEN 1 ELSE 0 END) AS vitorias,
       SUM(CASE WHEN g.EndReason = 'DRAW' THEN 1 ELSE 0 END) AS empates,
       SUM(CASE WHEN g.WinnerId IS NOT NULL AND g.WinnerId <> u.Id THEN 1 ELSE 0 END) AS derrotas,
       COUNT(*) AS partidas
FROM Users u
JOIN Games g ON g.PlayerXId = u.Id OR g.PlayerOId = u.Id
WHERE g.Status = 'FINISHED'
GROUP BY u.Id
ORDER BY vitorias DESC, empates DESC, u.Username
LIMIT 20;
```
