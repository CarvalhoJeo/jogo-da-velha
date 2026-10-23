# Guideline: README do projeto

O README é a porta de entrada do projeto. É o primeiro arquivo que o professor abre no GitHub e precisa permitir clonar, rodar e entender o sistema em poucos minutos, sem perguntar nada a ninguém. A avaliação cobra código bem documentado e documentação das decisões, e um README claro é a prova mais visível disso.

## Quando atualizar

Em todo PR que mudar como o sistema roda ou se comporta. Isso está no checklist do template de PR.

## Seções obrigatórias

1. **Visão geral:** o que é o jogo, uma frase sobre a arquitetura e um GIF ou print de uma partida.
2. **Requisitos:** Java 21, Maven e o sistema operacional testado.
3. **Como rodar:** comandos exatos para gerar o certificado (`keytool`), subir o servidor e abrir dois clientes, com as portas usadas.
4. **Como testar:** `mvn test` e como reproduzir os testes de segurança e de carga.
5. **Arquitetura e protocolo:** o diagrama de arquitetura e um link para a tabela de mensagens em `docs/`.
6. **Segurança:** o que é protegido e como (TLS, PBKDF2, validação no servidor).
7. **Requisitos atendidos:** a tabela de rastreabilidade com o status de cada um dos 10 requisitos base e dos opcionais.
8. **Monitoramento e depuração:** onde ficam os logs, como abrir o painel admin (se existir) e como capturar o tráfego no Wireshark.
9. **Limitações conhecidas e próximos passos.**

## Boas práticas

- Comandos copiáveis em blocos de código.
- Nenhum passo implícito: se precisa de algo instalado ou configurado, está escrito.
- Prints e GIFs atualizados com a versão atual.
- Links relativos para `docs/`.
- Teste final: um colega clona o repositório do zero e consegue rodar seguindo só o README.
