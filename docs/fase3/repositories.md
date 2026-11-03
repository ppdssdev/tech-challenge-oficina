# Repositórios e ownership da Fase 3

Grupo 97 · Pedro Paulo dos Santos Silva · Discord pedropaulo6447.
[Baseline e estado operacional](README.md). O bootstrap de outubro é histórico;
os quatro repositórios contêm implementação, e staging foi observado em execução.

| Repositório | Responsabilidade e fronteira | Documentação |
|---|---|---|
| [API](https://github.com/ppdssdev/tech-challenge-oficina) | Domínio Java, Flyway V1–V11, contratos SQL, grants, manifests e release da API | [README](../../README.md) |
| [Auth](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda) | Python CPF/JWT, Lambda, HTTP API, IAM auth, logs, assinatura de artefato | [README](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/main/README.md) |
| [K3s](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s) | VPC/subnets/rotas, SG app/auth, EC2/K3s, IAM nó, ECR e endpoint Secrets Manager | [README](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/main/README.md) |
| [RDS](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds) | RDS, DB subnet group, SG PostgreSQL e backup configurado | [README](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/blob/main/README.md) |

Contrato de rede `integration_contract` v3: schema_version, account_id, region,
environment, vpc_id, private_subnet_ids, app_security_group_id e auth_security_group_id.
Endpoint Secrets Manager é output separado. RDS publica identificador, endpoint,
porta, nome e SG; segredo administrativo não integra contrato de runtime da Lambda.
Migrations e privilégios SQL pertencem à API; auth consome view V2 sem copiar schema.

Governança observada por API em 2026-11-03: PR/checks obrigatórios e enforce_admins
nos quatro repos, mas **zero aprovações humanas exigidas**. soat-architecture está
adicionado somente na API; nos outros três não consta como colaborador. Nenhuma
regra/colaborador foi alterado. [Detalhes, comandos e OIDC](governance-audit.md).
Leitura pública não satisfaz a exigência de adicionar a banca em todos os repos.
