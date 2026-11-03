# Fase 3 — Oficina Mecânica, Grupo 97

Participante: **Pedro Paulo dos Santos Silva** · Discord: **pedropaulo6447**.
Auditoria documental e operacional: **2026-11-03**, região staging `us-east-2`.

A entrega distingue código versionado, observação operacional e proposta. A API está
implantada em staging; isso não certifica todos os requisitos da Fase 3. Não há
produção implantada demonstrada. As consultas desta auditoria foram somente leitura;
não houve deploy, alteração de infraestrutura, pipeline, banco remoto ou state.

## Roteiro de avaliação

1. [Arquitetura: componentes e implantação](deployment-architecture.md).
2. [Sequências de autenticação e processamento de OS](sequences.md).
3. [Modelo físico V1–V11 e dicionário](data-model.md) e [justificativa PostgreSQL](database-design.md).
4. [RFCs e ADRs](decisions.md), [CI/CD](cicd-strategy.md) e [responsabilidades dos repositórios](repositories.md).
5. [Matriz requisito → implementação → validação](evidence-matrix.md).
6. [Governança e OIDC auditados](governance-audit.md).
7. [Evidência operacional sanitizada](operational-evidence.md), [limitações e custos](trade-offs.md) e [validação documental](documentation-validation.md).

## Estado executivo

| Aspecto | Resultado desta auditoria |
|---|---|
| EC2/K3s | Um nó Ready, EC2 `m7i-flex.large`, K3s `v1.34.6+k3s1`; não há HA de nó |
| API | 3/3 réplicas Ready; ClusterIP 8080; nenhum Ingress; imagem por digest |
| HPA | 1–3 réplicas; CPU 70%, memória 75%; métricas disponíveis; ensaio de carga não realizado |
| RDS | PostgreSQL 16.15 disponível, privado, criptografado, Single-AZ, backup 1 dia |
| Lambda/API Gateway | Lambda Active com concorrência **zero**; HTTP API sem endpoint habilitado; rota AWS_IAM |
| Observabilidade | Metrics Server operacional; Prometheus/Grafana apenas no código local, ausentes do inventário K3s consultado; tracing não comprovado |
| CI/CD | CI main verde nos quatro SHAs abaixo; sem CD AWS em main; OIDC em PRs abertas, auditoria bloqueada por SCP |
| Autenticação E2E | Código e testes presentes; relato anterior de sucesso; não reexecutada com Lambda real nesta auditoria |

## Baseline imutável

| Repositório | SHA auditado | Escopo |
|---|---|---|
| [tech-challenge-oficina](https://github.com/ppdssdev/tech-challenge-oficina) | `469984555ea3b1d0d40580ada20144e2d01550b1` | Java, migrations, manifests, observabilidade local |
| [tech-challenge-oficina-auth-lambda](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda) | `1ff247717e35054967495ba7684742c67aaeb9da` | Python, contratos, Terraform auth |
| [tech-challenge-oficina-infra-k3s](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s) | `10fa81e88e8e5cd9ab06fe8661cf1a735be26675` | Rede, EC2, IAM, ECR, endpoint privado |
| [tech-challenge-oficina-infra-rds](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds) | `51bb0557156a42e0f6f71c05bc304a6e9af362bf` | PostgreSQL gerenciado e SG |

Os links de implementação usam esses SHAs sempre que cruzam repositórios. Links
`main` para os novos documentos passam a valer após os merges independentes das
PRs documentais; durante revisão, usar a branch de cada PR. Nenhum merge foi feito.

## Documentação histórica

Os relatórios de bootstrap, reconciliação e prompts registram resultados **na data
original**; não são o estado vigente. O relatório de reconciliação que dizia “Lambda
V1” foi superado pelo consumidor V2 em main. Os PNGs/PDFs de fases anteriores
permanecem como acervo: incluem GitLab, acesso anônimo e até identificação de outro
grupo em nome de arquivo. Não são diagramas nem comprovantes atuais da Fase 3.
A identificação desta entrega é Grupo 97. O edital oficial foi localizado posteriormente no workspace externo ao baseline e
cotejado com a matriz. Identificação/hash, requisitos e lacunas estão na
[matriz](evidence-matrix.md). Deploy automático, observabilidade cloud completa,
vídeo/PDF final e acesso da banca em três repositórios permanecem pendentes.
