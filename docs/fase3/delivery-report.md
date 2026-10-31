# Relatório de entrega — PR 3

Data: 2026-10-31. Bootstrap real de quatro repositórios; nenhum acesso à conta AWS,
recurso cloud, deploy, secret ou access key. Nenhum merge automático.

> Registro histórico do bootstrap inicial. A [revisão corretiva consolidada](corrective-review.md)
> atualiza governança, compatibilidade runtime/contratos e matriz de evidências.
> O avaliador já tem write na aplicação na consulta corretiva; a pendência de acesso
> descrita abaixo continua somente nos três novos repos. Nenhuma permissão foi
> alterada pela correção. CI aprovado não equivale a CD ou Fase 3 concluída.

## Repositórios e PRs

- Aplicação existente: https://github.com/ppdssdev/tech-challenge-oficina
- Criado: https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda
- Criado: https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s
- Criado: https://github.com/ppdssdev/tech-challenge-oficina-infra-rds

Novos repos inicializados com README mínimo; bootstrap em PRs subsequentes.
Branch comum: feature/fase3-multirepo-bootstrap. PRs abertas (sem merge):

- [Aplicação #3](https://github.com/ppdssdev/tech-challenge-oficina/pull/3)
- [Auth #1](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/1)
- [K3s #1](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/pull/1)
- [RDS #1](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/pull/1)

Resultados remotos são verificáveis na aba Checks de cada PR.

## Validação local

- Java 21 Maven clean verify: 137 testes, zero falhas/erros/skips; JaCoCo aprovado
  (mínimo 80% linhas dos pacotes de domínio). Inclui PostgreSQL Testcontainers.
- Docker build da aplicação aprovado.
- Kustomize + kubeconform strict Kubernetes 1.34: 18 recursos válidos, zero skips/erros.
- Terraform fmt/init -backend=false/validate aprovado nos dois novos repos e no
  Terraform local da aplicação. CLI local 1.17.1; CI fixada 1.14.6.
- Auth: Ruff, 5 testes de contrato e build ZIP/SHA256 aprovados. Python local 3.12,
  CI 3.13. Não são testes de autenticação implementada.
- Checkov 3.2.471 e política bootstrap aprovados nos dois novos repos. Sem recursos
  AWS, portanto sem cobertura de controles de infraestrutura real.

## Governança aplicada

Quatro branches main protegidas: PR, uma aprovação, checks estritos, conversas
resolvidas, revisão obsoleta descartada, admins incluídos, force-push/exclusão negados.
CODEOWNERS sugere proprietário sem tornar aprovação própria impossível de satisfazer.
staging e production criados, somente branches protegidas. production: reviewer
ppdssdev, prevent_self_review=true, can_admins_bypass=false. Nenhum job de deploy.

Pendente: convite soat-architecture. API recusou read em repositório pessoal (422);
a revisão automática bloqueou concessão de write por ampliar permissões sem
consentimento específico. Leitura pública disponível. Proprietário deve decidir
sobre write; não foi enviado convite nem concedida permissão adicional.
Outro revisor com permissão adequada é necessário para aprovar PRs do proprietário.
Antes de CD production, adicionar reviewer independente para execuções do proprietário.

## Contratos, decisões e riscos

Ver repositories.md, deployment-architecture.md, cicd-strategy.md e
aws-infrastructure-dependencies.md. Incluem dois diagramas Mermaid, interfaces RDS,
CPF/view read-only, HTTP API proxy 2.0, RS256/JWKS/rotação, ordem sem ciclos,
SSM outputs, state S3 locking, OIDC futuro, digest imutável, rollback e isolamento.
Infra de baixo custo proposta: K3s single-node EC2 + RDS privado Single-AZ, sem
EKS/NAT Gateway; orçamento preliminar 48–83 USD/mês/ambiente antes de créditos.
Limitações: ausência de HA, CPF não prova identidade, JWT HMAC atual ainda precisa
migração, revogação não imediata, elegibilidade e saldo Free Plan não verificados.

Próxima PR: handler Lambda com consulta e signer por portas, testes de CPF/status,
erros/timeouts, assinatura/rotação, contrato HTTP API e PostgreSQL. Coordenar view
Flyway e migração Spring Security RS256 antes de habilitar integração/deploy.

## Arquivos adicionados/modificados

### tech-challenge-oficina

- `.github/CODEOWNERS`
- `.github/dependabot.yml`
- `.github/pull_request_template.md`
- `.github/workflows/ci.yml`
- `.github/workflows/kind-smoke.yml`
- `README.md`
- `docs/fase3/aws-infrastructure-dependencies.md`
- `docs/fase3/cicd-strategy.md`
- `docs/fase3/deployment-architecture.md`
- `docs/fase3/repositories.md`

### tech-challenge-oficina-auth-lambda

- `.github/CODEOWNERS`
- `.github/dependabot.yml`
- `.github/pull_request_template.md`
- `.github/workflows/ci.yml`
- `.gitignore`
- `README.md`
- `contracts/error.schema.json`
- `contracts/request.schema.json`
- `contracts/response.schema.json`
- `requirements-dev.txt`
- `scripts/build.py`
- `tests/test_contracts.py`

### tech-challenge-oficina-infra-k3s

- `.github/CODEOWNERS`
- `.github/dependabot.yml`
- `.github/pull_request_template.md`
- `.github/workflows/ci.yml`
- `.gitignore`
- `README.md`
- `environments/production.tfvars.example`
- `environments/staging.tfvars.example`
- `outputs.tf`
- `scripts/bootstrap_policy.py`
- `variables.tf`
- `versions.tf`

### tech-challenge-oficina-infra-rds

- `.github/CODEOWNERS`
- `.github/dependabot.yml`
- `.github/pull_request_template.md`
- `.github/workflows/ci.yml`
- `.gitignore`
- `README.md`
- `environments/production.tfvars.example`
- `environments/staging.tfvars.example`
- `outputs.tf`
- `scripts/bootstrap_policy.py`
- `variables.tf`
- `versions.tf`

- `docs/fase3/delivery-report.md` (este relatório).
