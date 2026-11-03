# Registro de decisões

RFCs explicitam problema, alternativas, solução e aceite; ADRs registram escolhas
implementadas e seus limites. Todos os novos registros são consolidados em
2026-11-03 a partir de fontes verificadas, sem criar aprovações históricas fictícias.

## RFCs

- [RFC 0001 — AWS e K3s](rfc/0001-aws-k3s.md)
- [RFC 0002 — Banco de dados gerenciado](rfc/0002-postgresql.md)
- [RFC 0003 — Autenticação JWT e contratos de identidade](rfc/0003-jwt.md)
- [RFC 0004 — GitHub Actions e federação OIDC](rfc/0004-actions-oidc.md)
- [RFC 0005 — Observabilidade](rfc/0005-observability.md)

## ADRs

- [0001 — Arquitetura hexagonal (existente)](../adr/0001-arquitetura-hexagonal.md).
- [0005 — Domínios ADMIN/CUSTOMER (existente)](../adr/0005-admin-customer-trust-domains.md).
- [K3s single-node em vez de EKS](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/main/docs/adr/0001-k3s-single-node.md).
- [RDS privado e Single-AZ](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/blob/main/docs/adr/0001-rds-privado.md).
- [Lambda de autenticação sintética](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/main/docs/adr/0001-lambda-auth.md).
- [HPA no nó único](../adr/0006-hpa-single-node.md).
- [Terraform por repositório e contrato explícito](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/main/docs/adr/0002-terraform-repos.md).
- [Release da API por digest](../adr/0007-release-digest.md).

Os ADRs distribuídos pertencem ao repositório responsável pela decisão. Seus links
main ficam disponíveis após merge de cada PR documental; durante revisão, consultar
a branch da PR correspondente. A decisão de observabilidade cloud continua proposta,
explicitada na RFC 0005, sem inventar um deployment ou aprovação.
