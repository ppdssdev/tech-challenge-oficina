# CI atual, release manual e CD pendente

Estado em 2026-11-03; fontes imutáveis: [baseline](README.md).
As quatro mains executam CI em push/PR, com `contents: read`, actions fixadas por SHA
e sem credenciais AWS ou `id-token: write`. CI verde comprova execução dos checks,
não aplicação de Terraform nem deployment cloud.

| Repo | Validação em main | Execução consultada (success) |
|---|---|---|
| API | Maven/JaCoCo, Docker build, Terraform local, renderer e kubeconform; smoke Kind em workflow manual separado | [41636042180](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41636042180) |
| Lambda | Comando integral: Python 3.13, PostgreSQL/TLS e grants, Ruff, 11 testes Terraform mock, builds determinísticos e smoke ZIP | [41625965174](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/actions/runs/41625965174) |
| K3s | preflight Terraform/políticas/testes, ShellCheck/Checkov e TFLint | [41625905236](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/actions/runs/41625905236) |
| RDS | fmt/init sem backend/validate, política AST e Checkov | [41625940842](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/actions/runs/41625940842) |

Fontes: [workflow API](../../.github/workflows/ci.yml),
[.github/workflows/ci.yml](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/.github/workflows/ci.yml), [.github/workflows/ci.yml](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/10fa81e88e8e5cd9ab06fe8661cf1a735be26675/.github/workflows/ci.yml),
[.github/workflows/ci.yml](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/blob/51bb0557156a42e0f6f71c05bc304a6e9af362bf/.github/workflows/ci.yml). A Lambda fixa o contrato Java em
`57eb5df2c3e32fe5663596cbecfcd43a5d3ed780`; não deve consumir main móvel para testes
SQL. A consulta aos runs confirma conclusão success; não se atribuem números de
testes de relatórios antigos ao HEAD atual.

## OIDC e SCP

As PRs [API #10](https://github.com/ppdssdev/tech-challenge-oficina/pull/10),
[Auth #5](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/5),
[K3s #5](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/pull/5) e
[RDS #7](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/pull/7) estavam
abertas na auditoria. Preparam apenas validação manual STS, com trust por repo/main
e audience `sts.amazonaws.com`; não implementam CD completo. Roles propostas sem
identity policies não autorizam deploy/state/secrets. Na auditoria atual,
`iam:ListOpenIDConnectProviders` foi negado explicitamente por SCP. Resolver com
administração da organização; não contornar com credenciais permanentes nem reduzir
governança. A existência/configuração do provider e a federação real permanecem
não comprovadas. Além da SCP, a trust proposta usa subject por nomes, mas os três
repos novos usam prefixos imutáveis com IDs: precisam correção coordenada antes de
federação. [Consulta de governança/subjects](governance-audit.md). Dizer “CD bloqueado pela SCP” inclui esse impedimento, mas também
há trabalho de CD ainda não implementado; liberar a SCP não entrega CD sozinho.

## Release e rollback por digest

O [renderer](../../scripts/staging/render.sh) exige referência por digest. O
[runbook existente](../staging/release-local.md) e o script de release manual
tratam preflight, build/publicação e execução controlada via SSM; não são GitHub CD.
Fluxo: revisão limpa/CI → artefato identificado → ECR imutável → digest revisado →
Job Flyway separado se necessário → renderização → rollout → readiness → testes
ADMIN/CUSTOMER e ownership. A sequência descreve o procedimento; a auditoria atual
não executou release e não comprovou proveniência do digest em execução.

Runtime não carrega Flyway habilitado. O Job de migration não pertence ao
kustomization runtime e seu placeholder não é imagem válida de release. Manter
credencial migrator separada. `--dry-run` do release serve para revisão local;
`--execute` efetua mudanças e não foi chamado nesta tarefa.

Rollback recupera **digest anterior verificado**, confere compatibilidade com o
schema já aplicado e restaura runtime/configuração conforme runbook. Não reverter
migrations automaticamente, não publicar tag mutável para “voltar”, não interpretar
reversão de Git como rollback cloud. Produção requer ambiente/controles próprios;
não há produção implantada comprovada.

## Terraform independente

Cada stack usa backend S3 e chave própria `staging/k3s`, `staging/rds` ou
`staging/lambda`, com `use_lockfile=true` e `encrypt=true` no código. A existência,
versionamento, políticas e migração efetiva do backend não foram auditados lendo
state. Contratos de outputs explícitos evitam compartilhar credenciais e ownership;
ordem: K3s/rede → RDS → auth/API. Sem mudança de state nesta consolidação.
