# Repositórios da Fase 3

Estado: bootstrap, sem recursos AWS. Origem verificada em 2026-10-31:
main `ad7145df89ee347bb2ff6fcdebf8b3b45f70f6d7`. A cópia de portfólio
`ppdssdev/workshop-management-api` foi preservada, inclusive seus arquivos locais.

| Repositório | Responsabilidade | Check obrigatório |
|---|---|---|
| [Aplicação](https://github.com/ppdssdev/tech-challenge-oficina) | Java 21, domínio, PostgreSQL/Flyway, Docker e manifests da API | Maven verification, Docker build, Infrastructure validation |
| [Auth](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda) | Contratos, futura Lambda CPF/JWT e API Gateway | Auth contract and build |
| [K3s](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s) | Contratos Terraform de rede, EC2/K3s, IAM e registry | Terraform validation and security |
| [RDS](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds) | Contratos Terraform PostgreSQL, persistência e SG | Terraform validation and security |

Os três novos repositórios públicos foram criados com README mínimo em main.
A implementação subsequente usa `feature/fase3-multirepo-bootstrap` e PR, sem merge
automático. Não há cópia da implementação Java nos demais repositórios.
Terraform só contém metadados de interfaces; Lambda só empacota contratos.
A infraestrutura local existente permanece como ferramenta de desenvolvimento da API.

## Governança

main: PR obrigatório, uma aprovação, CODEOWNERS como sugestão de revisor, descarte de aprovações
após alterações, resolução de conversas, checks obrigatórios com branch atualizada,
regras inclusive para administradores, sem force-push e sem exclusão.
CODEOWNERS indica ppdssdev; o autor não pode aprovar a própria PR. Outro colaborador
com permissão adequada precisa revisar; convite de avaliador não equivale a aprovação.
Acesso do avaliador deve ser comprovado em cada repositório conforme edital; leitura
pública não demonstra presença como colaborador. Estado atual registrado abaixo.

Environments staging e production já existem, restritos a branches protegidas.
production: reviewer ppdssdev, impedir autoaprovação e bypass administrativo. Para um
fluxo iniciado pelo próprio proprietário, adicionar outro reviewer autorizado antes
ativar CD. Nenhum secret, role AWS ou job de deployment é criado nesta PR.

Procedimento manual se API GitHub negar: Settings → Branches → proteção main;
Settings → Environments → staging/production; Settings → Collaborators → convidar
soat-architecture. Conferir os nomes de checks na tabela após a primeira execução.
Se o plano não permitir proteção, manter CD desabilitado até resolver a limitação;
não substituir aprovação por um simples workflow_dispatch.

[Disponibilidade das proteções](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
e [environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

Estado aplicado: proteção main e environments configurados nos quatro repositórios.
Na revisão corretiva, a API confirmou `soat-architecture` com write na aplicação;
nos três novos repos, somente ppdssdev aparece como colaborador e não há convites
pendentes. Essa consulta é somente leitura; nenhuma permissão foi alterada nesta
correção. A recusa anterior de read/write descrita no relatório original é histórica,
não descreve mais o acesso atual da aplicação. O proprietário deve resolver o acesso
de revisão nos outros repos em procedimento separado, sem reduzir proteções.
CODEOWNERS não é check obrigatório pois o único owner também é autor das PRs.


As quatro PRs têm `mergeable=true`, `mergeable_state=blocked` e
`reviewDecision=REVIEW_REQUIRED`: uma aprovação exigida, zero reviews registrados.
Checks iniciais verdes; sem rulesets adicionais ou threads pendentes na consulta.
`enforce_admins=true` inclui o proprietário e o autor não pode aprovar a própria PR.
Production tem gate separado de deployment; não é a causa deste bloqueio de merge.
Ação manual: solicitar revisão a pessoa habilitada diferente do autor (na aplicação,
soat-architecture já tem write); nos novos repos, resolver a ausência de revisor
habilitado por decisão do proprietário fora desta correção. Aguardar aprovação no
HEAD final, checks atuais e conversas resolvidas. Não remover revisão, usar bypass
ou reduzir proteções. Ver [relatório corretivo](corrective-review.md).
