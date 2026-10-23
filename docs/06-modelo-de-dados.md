# Modelo de dados

Quatro tabelas cobrem cadastro, histórico, placar e monitoramento. O placar é uma consulta sobre `Games`, sem tabela própria, para nunca ficar inconsistente.

| Tabela | Colunas principais |
| --- | --- |
| `Users` | Id, Username (único), PasswordHash, Salt, Iterations, CreatedAt, LastLoginAt |
| `Games` | Id, PlayerXId, PlayerOId (nulo se bot), BotLevel, Status, WinnerId, EndReason (THREE_IN_ROW / DRAW / RESIGN / TIMEOUT), StartedAt, EndedAt |
| `Moves` | Id, GameId, PlayerId, Cell (0 a 8), MoveNumber, CreatedAt |
| `ServerEvents` | Id, Timestamp, Level (INFO / WARN / ERROR), Type (USER_REGISTERED, LOGIN, GAME_STARTED, GAME_ENDED, DISCONNECT, ERROR...), UserId, GameId, Details (JSON) |

A tabela `Moves` também permite reconstruir uma partida após queda do servidor e mostrar o replay na apresentação.

## Consulta do placar

```sql
SELECT u.Username,
       SUM(CASE WHEN g.WinnerId = u.Id THEN 1 ELSE 0 END) AS vitorias,
       SUM(CASE WHEN g.EndReason = 'DRAW' THEN 1 ELSE 0 END) AS empates
FROM Users u
JOIN Games g ON g.PlayerXId = u.Id OR g.PlayerOId = u.Id
WHERE g.Status = 'FINISHED'
GROUP BY u.Id
ORDER BY vitorias DESC, empates DESC;
```
