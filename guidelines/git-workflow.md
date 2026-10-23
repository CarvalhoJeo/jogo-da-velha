# Fluxo de trabalho com Git

Cada feature entra por uma branch própria e um Pull Request no GitHub antes de chegar na `main`, que sempre compila e passa nos testes. O guia pede um repositório limpo e o registro das contribuições do grupo: os PRs, com autor e revisor, são esse registro.

## Regras

1. **`main` protegida:** sem push direto, PR obrigatório e CI verde para o merge.
2. **Uma branch por feature**, criada a partir da `main` atualizada:
   - `feature/<nome>` para funcionalidade (ex.: `feature/login-tls`)
   - `fix/<nome>` para correção
   - `docs/<nome>` para documentação
   - `test/<nome>` para testes
   - `chore/<nome>` para build, CI e configuração
3. **Commits pequenos** no padrão Conventional Commits:
   - `feat: adiciona convite com expiração de 30 s`
   - `fix: rejeita jogada em casa ocupada`
   - `test: cobre as 8 linhas de vitória`
   - `docs: atualiza diagrama de sequência do convite`
   - `refactor:`, `chore:` quando fizer sentido
4. **Um PR por branch**, usando o [template](../.github/pull_request_template.md): o que mudou, qual requisito atende, como testar e evidências.
5. **CI:** GitHub Actions roda `mvn verify` em todo PR.
6. **Revisão:** um colega revisa; se o projeto for individual, autorrevisão pelo checklist do template.
7. **Merge com squash** e a branch é apagada depois.
8. **Tags por semana:** `v0.1` ao fim da semana 1 até `v1.0` na entrega.

## Branches previstas

| Semana | Branches |
| --- | --- |
| 1 | `docs/regras-e-arquitetura`, `docs/protocolo` |
| 2 | `chore/setup-maven-ci`, `feature/tls-framing`, `feature/auth` |
| 3 | `feature/presence`, `feature/invites`, `feature/javafx-client` |
| 4 | `feature/game-engine`, `feature/game-session`, `feature/security-hardening` |
| 5 | `test/e2e-e-borda`, `feature/event-logger` |
| 6 | `feature/reconnect`, `feature/leaderboard`, `docs/relatorio-final` |

## Repositório limpo

- `.gitignore` cobre: `target/`, arquivos de IDE (`.idea/`, `.vscode/`), `*.db`, `logs/`, keystore e truststore (`*.jks`, `*.p12`), capturas grandes que não forem evidência.
- Nenhum segredo commitado: senhas, keystores e tokens ficam fora do Git.
- Nada de arquivos temporários, binários gerados ou código comentado sem motivo.
- Capturas do Wireshark usadas como evidência ficam em `docs/evidencias/`, com nomes descritivos.
