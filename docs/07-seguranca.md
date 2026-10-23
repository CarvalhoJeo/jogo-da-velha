# Segurança

A defesa principal é TLS em todo o tráfego mais um servidor que nunca confia no cliente.

O enunciado cita "SHA-64", que não existe. O projeto usa SHA-256 dentro do PBKDF2, e isso deve ser mencionado na documentação final.

| Ameaça | Mitigação | Como demonstrar |
| --- | --- | --- |
| Senha vazada do banco | `PBKDF2WithHmacSHA256`, salt aleatório de 16 bytes (`SecureRandom`), 210k iterações | Mostrar a tabela `Users` sem senha legível |
| Sniffing na rede | TLS 1.3 via JSSE (`SSLServerSocket`) | Wireshark: com TLS só se vê `Application Data`; sem TLS, o JSON aparece em claro |
| Man in the Middle | Cliente confia só no certificado do servidor, carregado num truststore próprio | Proxy com certificado falso é rejeitado pelo cliente |
| Jogada trapaceada | Servidor valida vez, casa livre e estado; cliente só envia intenção | Enviar `MOVE` fora da vez ou em casa ocupada e receber `ERROR` |
| Sequestro de sessão | Token aleatório de 32 bytes por login, válido só na conexão TLS | Token antigo não funciona após logout |
| Força bruta no login | Limite de 5 tentativas por minuto por usuário e IP | Script de teste bloqueado após 5 tentativas |
| Flood / DoS simples | Quadro máximo de 64 KB, limite de mensagens por segundo, timeout de handshake | Teste de carga com muitas conexões |
| Enumeração de usuários | Mesma resposta para usuário inexistente e senha errada | Comparar respostas de `AUTH_FAIL` |

## Capturas exigidas pelo guia

- Uma sessão completa (cadastro, login e partida) capturada **antes** e **depois** de ativar o TLS.
- Arquivos `.pcapng` salvos com nomes descritivos, por exemplo `01_sessao_sem_tls.pcapng` e `02_sessao_com_tls.pcapng`.

## Segredos

- Keystore, truststore e senhas ficam fora do Git (`.gitignore`).
- O README explica como gerar o certificado com `keytool`.
