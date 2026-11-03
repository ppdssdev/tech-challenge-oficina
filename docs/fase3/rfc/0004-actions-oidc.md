# RFC 0004 — GitHub Actions e federação OIDC

Data: 2026-11-03. Autor da consolidação: documentação do Grupo 97.
Status: reconstrução auditável das escolhas implementadas e propostas remanescentes;
não registra aprovação retroativa. Escopo: staging acadêmico.

## Problema

Evitar credenciais AWS permanentes em CI e separar validação de deployment.

## Solução e estado

CI de main não acessa AWS. OIDC permanece proposto nas quatro PRs abertas: trust restrita ao repo/main e audience STS; validação manual GetCallerIdentity antes de CD e políticas mínimas por stack.

## Alternativas e trade-offs

Access keys permanentes aumentam armazenamento/rotação de segredos e não resolvem explicit deny de SCP. Roles administrativas amplas não são necessárias para validar STS. Não contornar governança da organização.

## Critérios de aceite e evolução

Resolver auditoria SCP, corrigir a incompatibilidade entre subjects imutáveis dos três repos novos e a trust draft por nomes, verificar provider/audience/trust e testar federação real; só então desenhar permissões de deploy/state por chave. Produção necessita aprovação e isolamento próprios.

Mudanças futuras precisam de PR específica, validação operacional e revisão do impacto;
esta RFC não executa mudanças nem fecha pendências por declaração.

## Fontes

[CI/CD e PRs](../cicd-strategy.md), [registro do deny](../operational-evidence.md).

[Governança e subjects observados](../governance-audit.md).
