# Guideline: testes

O guia pede um plano de testes escrito antes da execução. Os casos estão em [10-plano-de-testes.md](../docs/10-plano-de-testes.md).

## Regras

- Pelo menos um teste por requisito base.
- Todos os resultados do jogo cobertos: vitória, derrota, empate e desistência.
- Testes de ponta a ponta com 2 clientes reais em máquinas diferentes.
- Casos de borda: app fechado no meio da partida, servidor reiniciado, mensagem malformada, usuário duplicado, credenciais erradas.
- Regras do jogo (`TicTacToeEngine`) testadas com JUnit 5, sem rede.
- Todo PR que adiciona comportamento adiciona o teste correspondente.

## Evidências

- Resultado de cada execução registrado na tabela do plano (passou ou falhou, data, observação).
- Capturas do Wireshark de uma sessão completa (cadastro, login, partida), antes e depois do TLS.
- Nomes descritivos: `01_sessao_sem_tls.pcapng`, `02_sessao_com_tls.pcapng`, `03_mitm_rejeitado.png`.
- Tudo salvo em `docs/evidencias/`.
