# Decisões técnicas

A stack usa Java, uma das linguagens preferidas no guia, com sockets TCP puros, para que a programação de sockets e o tráfego no Wireshark fiquem visíveis em vez de escondidos atrás de um framework.

| Camada | Escolha | Por quê |
| --- | --- | --- |
| Linguagem | Java 21 (LTS) | Preferida no guia; virtual threads simplificam uma thread por conexão |
| Build | Maven, com módulos `server`, `client` e `common` | `common` guarda o protocolo e as regras, compartilhados pelos dois lados |
| Transporte | `SSLServerSocket` / `SSLSocket` (JSSE) | Sockets explícitos na camada 4, com TLS integrado |
| Segurança do canal | TLS 1.3 com certificado autoassinado gerado pelo `keytool` | Confidencialidade e integridade, verificável no Wireshark |
| Enquadramento | `DataOutputStream.writeInt(tamanho)` + JSON UTF-8 | Resolve o problema de TCP ser fluxo de bytes, sem mensagens |
| Serialização | Jackson | Biblioteca padrão de mercado para JSON em Java |
| Senhas | PBKDF2 (`PBKDF2WithHmacSHA256`, 210k iterações, salt por usuário) | Nativo no JDK, hash lento e com salt |
| Banco | SQLite via JDBC (`sqlite-jdbc`) | Zero instalação, fácil de entregar e demonstrar |
| Barramento de eventos | `BlockingQueue` por partida + um consumidor por fila | Fila de ações e turnos, conforme o conceito de event bus do curso |
| Cliente | JavaFX | Interface gráfica simples, nativa do ecossistema Java |
| Painel admin (se sobrar tempo) | `com.sun.net.httpserver.HttpServer` | Embutido no JDK, sem framework extra |
| Logs | SLF4J + Logback, mais a tabela `ServerEvents` | Eventos do servidor em arquivo e no banco |
| Testes | JUnit 5 | Padrão para testes em Java |
| Versionamento | Git + GitHub, uma branch e um PR por feature | Exigido no guia; ver [git-workflow.md](../guidelines/git-workflow.md) |

## Por que não MQTT

O guia aceita MQTT com broker como opcional, mas pede justificativa se não for usado.

- Sockets TCP diretos deixam o protocolo, o enquadramento e o TLS sob nosso controle e visíveis no Wireshark, que é o foco do curso.
- Um broker esconderia parte disso e acrescentaria mais um componente para instalar, proteger e monitorar.
- O roteamento por partida que o broker faria fica no `MessageRouter`, com as filas de cada `GameSession`.

## Por que sockets e não um framework web

- O guia pede comunicação por sockets sobre TCP/IP.
- WebSocket ou HTTP esconderiam o enquadramento e o ciclo de vida da conexão, que são justamente o que a disciplina quer ver.
