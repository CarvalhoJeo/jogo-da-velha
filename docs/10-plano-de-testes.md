# Plano de testes

O plano é escrito antes da execução, como o guia pede, com pelo menos um teste por requisito base, todos os resultados possíveis do jogo e testes de ponta a ponta com 2 clientes em máquinas diferentes. Regras gerais em [testes.md](../guidelines/testes.md).

| Tipo | Caso | Resultado esperado | Req. |
| --- | --- | --- | --- |
| Unitário | Vitória nas 3 linhas, 3 colunas e 2 diagonais | `checkWinner` retorna o jogador certo nas 8 combinações | 7 |
| Unitário | Tabuleiro cheio sem 3 em linha | Empate | 7 |
| Unitário | Jogada em casa ocupada ou fora de 0 a 8 | Jogada rejeitada | 6 |
| Unitário | Hash de senha | Salts diferentes geram hashes diferentes; verificação correta | 1 |
| Ponta a ponta | Cadastro, login, usuário duplicado e senha errada | `AUTH_OK`; duplicado e senha errada geram erro genérico | 1, 2 |
| Ponta a ponta | Lista de online ao entrar e sair | Lista atualizada nos dois clientes | 3 |
| Ponta a ponta | Convite aceito, recusado e expirado | Partida criada só no aceito | 4, 5 |
| Ponta a ponta | Partida em 2 máquinas até vitória, derrota e empate | `GAME_OVER` correto para os dois lados | 6, 7 |
| Ponta a ponta | Desistência | Oponente recebe WIN com motivo RESIGN | 8 |
| Ponta a ponta | Eventos de login, partida e erro | Registrados em arquivo e em `ServerEvents` | 9 |
| Carga | 50 clientes e 25 partidas simultâneas | Servidor estável, sem perda de mensagens | 10 |
| Borda | App fechado no meio da partida | Partida pausa e retoma com reconexão, ou oponente vence por timeout | 8, Reconexão |
| Borda | Servidor reiniciado | Clientes recebem erro claro e voltam ao login; partidas em andamento ficam registradas | 9 |
| Borda | Mensagem malformada ou maior que 64 KB | `ERROR` e conexão mantida ou encerrada, sem derrubar o servidor | 9 |
| Borda | Jogada fora da vez | `ERROR`, tabuleiro inalterado | 6 |
| Segurança | Captura de uma sessão completa antes e depois do TLS | Antes: JSON e senha legíveis; depois: só dados cifrados | 2 |
| Segurança | Proxy MitM com certificado falso (mitmproxy) | Cliente recusa a conexão | 2 |
| Segurança | 6 logins errados em 1 minuto | Bloqueio temporário | 2 |
| Opcional | Placar após várias partidas | Vitórias e empates corretos e ordenados | Placar |
