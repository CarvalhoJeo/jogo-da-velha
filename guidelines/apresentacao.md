# Guideline: apresentação

A apresentação é feita no **Google Apresentações**, compartilhada com o professor por link, com cerca de 15 minutos: 5 de slides e 10 de demo ao vivo. Cada momento da demo aponta qual requisito ou conceito está sendo provado.

**Montagem:** servidor numa VM (ou na nuvem), dois clientes JavaFX em máquinas diferentes, terminal com os logs e Wireshark capturando a porta do servidor.

## Roteiro da demo (ordem sugerida pelo guia)

| Tempo | Momento | O que mostrar | Prova |
| --- | --- | --- | --- |
| 0:00 | Slides 1 a 3 | Jogo escolhido, stack e arquitetura | Arquitetura cliente-servidor |
| 2:00 | Slides 4 e 5 | Protocolo, diagramas de sequência e estados | Sockets, IPC e event bus |
| 5:00 | Servidor | Subir o servidor e mostrar o log de inicialização | Req. 9 |
| 5:30 | Cadastro e login | Cadastrar e logar A e B em máquinas diferentes; mostrar a tabela `Users` com hash | Req. 1 e 2 |
| 7:00 | Lobby | B aparece online para A; A convida B; B aceita | Req. 3, 4 e 5 |
| 8:00 | Partida | Partida completa até o fim; resultado nas duas telas | Req. 6 e 7 |
| 9:30 | Desistência | Segunda partida em que B desiste | Req. 8 |
| 10:30 | Opcionais | Reconexão: derrubar a rede de B e voltar; placar atualizado | Opcionais escolhidos |
| 11:30 | Logs | Arquivo de log e tabela `ServerEvents` | Req. 9 |
| 12:30 | Wireshark | Captura antes do TLS (JSON legível) e depois (só `Application Data`) | Confidencialidade |
| 14:00 | Encerramento | Lições aprendidas e perguntas | |

## Slides

1. Título, aluno(s) e objetivo do projeto.
2. Jogo escolhido, stack e por que Java + sockets puros.
3. Diagrama de arquitetura.
4. Protocolo: formato do quadro e principais mensagens.
5. Diagramas de sequência e de estados.
6. Segurança: ameaças e mitigações.
7. Testes realizados e fluxo de branches e PRs no GitHub.
8. Lições aprendidas e próximos passos.

## Boas práticas

**Slides**

- Uma ideia por slide, com o título escrito como afirmação ("O servidor valida toda jogada"), e não como tema ("Validação").
- No máximo 6 linhas ou cerca de 30 palavras por slide; o resto vai para as notas do orador.
- Diagramas no lugar de texto, exportados de `docs/`.
- Fonte de corpo com pelo menos 24 pt e títulos com 36 pt, tema único, alto contraste, só transições simples.
- Código só em trechos curtos e destacados (o quadro do protocolo, uma mensagem JSON), nunca uma classe inteira.
- Slides numerados e um slide final com o link do repositório e um QR code.

**Fala e ensaio**

- Roteiro de fala nas notas do orador, usando o modo apresentador.
- Para cada conceito: explicar em uma frase e logo mostrar a prova na demo.
- Falar para a turma, não ler o slide; no máximo 1 minuto por slide.
- Ensaiar ao menos 2 vezes com cronômetro, uma delas na máquina e na rede da apresentação.

**Demo**

- Servidor, clientes, logs e Wireshark abertos antes de começar, com zoom aumentado no terminal e no Wireshark.
- Notificações desligadas; contas e dados de teste preparados, sem dados pessoais.
- Plano B: vídeo da demo completa gravado na semana anterior, mais os slides baixados em PDF e PPTX.

**Perguntas**

- Reservar 2 a 3 minutos.
- Preparar respostas para as prováveis: por que TCP e não UDP, por que TLS, como o servidor impede trapaça, o que acontece se o servidor cair, por que sockets puros em vez de um framework.

**Domínio do projeto:** o guia exige que todo membro saiba explicar qualquer parte. Antes da apresentação, cada membro faz uma rodada explicando um componente que não implementou, usando os PRs e o README como roteiro.
