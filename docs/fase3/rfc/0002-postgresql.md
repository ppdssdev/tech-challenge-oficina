# RFC 0002 — Banco de dados gerenciado

Data: 2026-11-03. Autor da consolidação: documentação do Grupo 97.
Status: reconstrução auditável das escolhas implementadas e propostas remanescentes;
não registra aprovação retroativa. Escopo: staging acadêmico.

## Problema

Manter integridade entre orçamento, estoque, cliente, veículo, OS e histórico.

## Solução e estado

PostgreSQL 16 em RDS privado, Single-AZ; migrations pertencem à API; transações, constraints e usuários SQL separados. V2 expõe identidade mínima para auth.

## Alternativas e trade-offs

Banco no mesmo nó K3s reduz serviços, mas acopla perda do nó aos dados. NoSQL exige redesenho das relações/transações; não foi adotado. Multi-AZ atende disponibilidade maior, com custo adicional.

## Critérios de aceite e evolução

Validar V1–V11 e grants com PostgreSQL real, manter checksums e medir planos dos índices. Testar restauração e documentar RPO/RTO antes de prometer recuperação.

Mudanças futuras precisam de PR específica, validação operacional e revisão do impacto;
esta RFC não executa mudanças nem fecha pendências por declaração.

## Fontes

[Modelo](../data-model.md), [justificativa](../database-design.md).
