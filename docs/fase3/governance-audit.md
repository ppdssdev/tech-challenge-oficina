# Governança e OIDC — consulta complementar

Auditoria em 2026-11-03. Um relatório complementar surgiu no workspace durante a
consolidação; seus achados motivaram consultas independentes. Arquivos originais
foram preservados. O conector GitHub retornou 403 para proteção administrativa;
a CLI autenticada permitiu a leitura. Nenhuma configuração foi modificada.

| Repositório | Aprovações exigidas em main | Checks | soat-architecture em collaborators | Subject OIDC configurado |
|---|---|---|---|---|
| API | 0 | Maven verification; Docker build; Infrastructure validation | Sim, write | Prefixo por nomes, use_immutable_subject=false |
| Auth | 0 | Auth contract and build | Não | Prefixo imutável com IDs |
| K3s | 0 | Terraform validation and security | Não | Prefixo imutável com IDs |
| RDS | 0 | Terraform validation and security | Não | Prefixo imutável com IDs |

`required_pull_request_reviews` está presente e `enforce_admins=true` nos quatro.
PR/checks obrigatórios não implicam revisão humana independente; os relatórios de
bootstrap que falavam em uma aprovação não representam a configuração atual.
O edital exige main protegida/PR; não se inventa exigência textual de uma aprovação,
mas a ausência de revisão independente é um risco de governança explícito.

A banca precisa ser adicionada também nos três repos de infraestrutura/auth para
atender ao entregável do edital. Leitura de repositório público não comprova esse
ato. A auditoria não enviou convites nem alterou permissões.

## Divergência na proposta OIDC

A PR K3s #5, SHA `259c1a233bc0243b9f850b5415fc03b3c8231fd5`, arquivo
`github_oidc.tf`, compara sub com `repo:ppdssdev/REPOSITORIO:ref:refs/heads/main`.
A API usa prefixo por nomes, mas os demais retornaram `use_immutable_subject=true`
e prefixo `repo:ppdssdev@OWNER_ID/REPOSITORIO@REPO_ID`. Acrescentar o sufixo da main
a esses prefixos não corresponde à trust por nomes. Isso é comparação de
configuração, não captura de JWT nem teste de AssumeRoleWithWebIdentity.

Portanto, resolver apenas a SCP não basta: revisar trust/formato efetivo nos três
repos, provider/audience, contexto de branch/environment e depois executar teste
real STS. Não desabilitar subjects imutáveis como contorno documental. As PRs
preparatórias continuam abertas; nenhum arquivo Terraform/workflow foi corrigido
nesta entrega exclusivamente documental.

## Fontes e reprodução

Para cada um dos quatro nomes de repo da [baseline](README.md):

```bash
gh api repos/ppdssdev/REPOSITORIO/branches/main/protection \
  --jq '{approvals:.required_pull_request_reviews.required_approving_review_count,checks:.required_status_checks.contexts,enforce_admins:.enforce_admins.enabled}'
gh api repos/ppdssdev/REPOSITORIO/actions/oidc/customization/sub
gh api repos/ppdssdev/REPOSITORIO/collaborators --jq 'map({login:.login,role:.role_name})'
```

Fontes de configuração:
[proteção API](https://api.github.com/repos/ppdssdev/tech-challenge-oficina/branches/main/protection),
[proteção auth](https://api.github.com/repos/ppdssdev/tech-challenge-oficina-auth-lambda/branches/main/protection),
[proteção K3s](https://api.github.com/repos/ppdssdev/tech-challenge-oficina-infra-k3s/branches/main/protection),
[proteção RDS](https://api.github.com/repos/ppdssdev/tech-challenge-oficina-infra-rds/branches/main/protection)
e [PR K3s #5](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/pull/5).
APIs administrativas exigem credencial autorizada; não são links públicos de prova.
O bloqueio SCP reproduzido está no [registro AWS](operational-evidence.md).
