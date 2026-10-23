# Monitoramento e depuração

O servidor registra todo evento relevante em arquivo (Logback) e na tabela `ServerEvents`, cobrindo o requisito 9 do guia.

## Eventos registrados

| Categoria | Eventos |
| --- | --- |
| Jogadores | Conexão, cadastro, login, logout, desconexão |
| Partidas | Convite, início e fim (com participantes e motivo), desistência, timeout |
| Erros | Mensagem inválida ou malformada, exceções, falhas de rede, tentativas de login bloqueadas |

Cada registro tem horário, nível (INFO / WARN / ERROR), tipo, usuário, partida e detalhes em JSON.

## Ferramentas de depuração

- **Logs:** `logs/server.log` com rotação diária.
- **Banco:** consultas em `ServerEvents` para reconstruir o que aconteceu.
- **Wireshark:** filtro `tcp.port == <porta>` para ver o handshake TLS e o tráfego cifrado.
- **TLS key log (só em teste):** com o key log do Java ativado, o Wireshark decifra o tráfego e mostra o JSON, útil para depurar o protocolo sem desligar o TLS.
- **Painel admin (opcional):** usuários online, jogos ativos, conexões e erros em tempo real.
