# Cronograma

O cronograma segue as 6 semanas e as fases 0 a 11 do guia. Cada semana termina com algo que roda e pode ser mostrado, e a documentação é escrita junto, não no fim.

| Semana | Fases do guia | Entregável | Pronto quando |
| --- | --- | --- | --- |
| 1 | 0. Escolha do jogo e 1. Projeto | Regras do Jogo da Velha por escrito, requisitos, arquitetura, protocolo e diagramas iniciais | Professor aprovou jogo e stack |
| 2 | 2. Ambiente e 3. Servidor (parte 1) | Repo Git com Maven multimódulo, CI, servidor TLS com enquadramento, `REGISTER`/`LOGIN`, SQLite | Dois clientes de console fazem login |
| 3 | 3. Servidor (parte 2) e 4. Cliente | Presença, convites, cliente JavaFX com login e lobby | A convida B e B vê o convite em tempo real |
| 4 | 5. Sincronização e 6. Segurança | `TicTacToeEngine`, fila por partida, `MOVE`, `GAME_OVER`, `RESIGN`; TLS e PBKDF2 conferidos; capturas antes e depois da proteção | Partida completa entre 2 máquinas; Wireshark só mostra tráfego cifrado |
| 5 | 7. Testes e 8. Monitoramento | Plano de testes executado, casos de borda, teste de carga, `ServerEvents` completo | Todos os testes base passando, com evidências |
| 6 | 9. Extras, 10. Documentação e 11. Apresentação | Reconexão e placar, documentação final, README, slides e vídeo de backup | Demo ensaiada do início ao fim |

## Se o prazo apertar

- Os requisitos base 1 a 10 nunca saem.
- Os opcionais saem nesta ordem: nuvem, painel, bot, placar, reconexão.
- O guia avisa para não começar opcionais antes da base funcionar.

## Tags de versão

Ao fim de cada semana, uma tag no Git: `v0.1` (semana 1) até `v1.0` (entrega). Ver [git-workflow.md](../guidelines/git-workflow.md).
