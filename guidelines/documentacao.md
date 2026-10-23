# Guideline: documentação

O guia do professor exige que a documentação seja escrita durante o desenvolvimento, não no fim.

## Regras

- Todo PR que muda protocolo, arquitetura ou comportamento atualiza `docs/` no mesmo PR.
- Diagramas em Mermaid (renderiza direto no GitHub) ou PlantUML, versionados junto com o código, nunca só como imagem solta.
- Exemplos de payload copiados de mensagens reais, não inventados.
- Toda decisão relevante registra o porquê em `docs/03-decisoes-tecnicas.md` (ex.: por que não MQTT).
- Evidências (prints, capturas `.pcapng`) em `docs/evidencias/`, com nomes descritivos.

## Estrutura do relatório final

As 8 seções sugeridas pelo professor, mapeadas em [11-documentacao.md](../docs/11-documentacao.md):

1. Visão geral e escolha do jogo
2. Requisitos
3. Arquitetura
4. Protocolo e payloads
5. Segurança
6. Testes
7. Monitoramento
8. Decisões e justificativas

## Estilo

- Frases curtas e diretas; uma ideia por parágrafo.
- Tabelas para comparações e listas de mensagens.
- Termos técnicos consistentes em todo o projeto (ex.: sempre "partida", nunca alternar com "sala").
