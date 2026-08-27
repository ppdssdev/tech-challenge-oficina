# ADR 0001 — Arquitetura hexagonal no monólito modular

- Status: aceito
- Data: 2026-08-26

## Contexto

O domínio era também o modelo JPA, e os serviços de aplicação conheciam DTOs REST,
Spring Data e transações Spring. Isso invertia a direção desejada das dependências e
concentrava todos os fluxos de ordem de serviço em uma única classe.

## Decisão

Adotar arquitetura hexagonal dentro do mesmo monólito:

- `domain` contém entidades, objetos de valor, regras e serviços puros;
- `application/port/in` publica os casos de uso e seus comandos;
- `application/port/out` abstrai persistência, transação, segurança e notificação;
- `application/usecase` implementa os fluxos sem dependências de framework;
- `adapters/in/web` mantém URLs, payloads, validação e respostas HTTP;
- `adapters/out/persistence` contém entidades JPA, Spring Data, locks e mapeadores;
- `adapters/out/security` preserva autenticação e emissão/validação JWT;
- `configuration` é o composition root Spring.

As ordens de serviço foram separadas em criar, consultar, listar, iniciar e atualizar
diagnóstico, adicionar itens, decidir orçamento, finalizar, entregar e calcular
métricas. Mapeadores explícitos separam domínio, JPA e DTOs. O lock pessimista da OS e
das peças é mantido durante a mesma transação, com peças bloqueadas em ordem estável.

## Consequências

O domínio e a aplicação podem ser testados sem Spring/JPA, enquanto detalhes de banco
e web podem evoluir independentemente. Há mais tipos e mapeamento explícito, aceitos
como custo de isolamento.

Não houve alteração de schema nesta decisão; portanto nenhuma migration Flyway foi
adicionada ou modificada.
