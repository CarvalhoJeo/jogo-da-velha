# Visão geral

O projeto é um **Jogo da Velha (Tic Tac Toe) online para 2 jogadores**, em Java 21, com servidor TCP próprio sobre TLS, mensagens JSON e banco SQLite.

O servidor é a única fonte da verdade do estado do jogo: o cliente só envia intenções ("marcar a casa 4") e recebe o estado validado.

## Por que Jogo da Velha

- Está entre os jogos sugeridos pelo professor.
- Tem regras mínimas (tabuleiro 3x3, 8 linhas de vitória), então quase todo o esforço vai para o que o curso avalia: rede, segurança, eventos e robustez.

## Regras do jogo

1. Tabuleiro 3x3, casas numeradas de 0 a 8 (esquerda para direita, cima para baixo).
2. Dois jogadores, X e O; X começa.
3. Cada jogador marca uma casa livre na sua vez.
4. Vence quem completar uma linha, coluna ou diagonal.
5. Tabuleiro cheio sem vencedor é empate.
6. Um jogador pode desistir a qualquer momento, e o oponente vence.

## Escopo

- Os 10 requisitos base do guia vêm primeiro.
- O guia recomenda escolher 1 ou 2 opcionais e documentá-los bem. Os escolhidos são **reconexão** e **placar**.
- Bot, painel admin e nuvem só entram se sobrar tempo, com a base pronta e testada.

Detalhes em [02-requisitos.md](02-requisitos.md).
