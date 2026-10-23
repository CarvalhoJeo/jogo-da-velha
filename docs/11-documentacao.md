# Documentação final

O relatório final segue as 8 seções sugeridas pelo professor. Cada seção já tem base num arquivo deste plano, e os diagramas ficam no repositório em Mermaid ou PlantUML. Regras de escrita em [documentacao.md](../guidelines/documentacao.md).

| Seção do guia | Conteúdo | Base neste plano |
| --- | --- | --- |
| 1. Visão geral e escolha do jogo | Regras do Jogo da Velha e por que ele | [01-visao-geral.md](01-visao-geral.md) |
| 2. Requisitos | Os 10 base, os opcionais escolhidos e os não funcionais | [02-requisitos.md](02-requisitos.md) |
| 3. Arquitetura | Cliente, servidor e banco; fluxos de rede e IPC | [04-arquitetura.md](04-arquitetura.md) |
| 4. Protocolo e payloads | Quadro, tabela de mensagens, exemplos reais de payload; diagramas de sequência e de estados | [05-protocolo.md](05-protocolo.md) |
| 5. Segurança | Ameaças, mitigações e capturas antes e depois do TLS | [07-seguranca.md](07-seguranca.md) |
| 6. Testes | Plano, execução e evidências | [10-plano-de-testes.md](10-plano-de-testes.md) |
| 7. Monitoramento | Logs, `ServerEvents`, depuração com Wireshark | [08-monitoramento.md](08-monitoramento.md) |
| 8. Decisões e justificativas | Bibliotecas, sockets puros, por que não MQTT, opcionais escolhidos | [03-decisoes-tecnicas.md](03-decisoes-tecnicas.md) |

## Diagramas exigidos pelo guia

- [x] Diagrama de estados da partida ([05-protocolo.md](05-protocolo.md))
- [x] Sequência de um convite ([05-protocolo.md](05-protocolo.md))
- [x] Sequência de uma jogada ([05-protocolo.md](05-protocolo.md))
- [x] Fluxos de rede e componentes ([04-arquitetura.md](04-arquitetura.md))
- [ ] Atualizar todos com o código final antes da entrega
