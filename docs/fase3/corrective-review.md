# Revisão corretiva consolidada — bootstrap Fase 3

Revisão de 2026-10-31, nas quatro PRs existentes. **Bootstrap revisável não equivale
a conclusão da Fase 3. Não há infraestrutura AWS implementada nem evidência de deploy.**
Esta correção modifica somente documentação; não altera domínio, migrations,
contratos funcionais, workflows, permissões, secrets ou proteções. Nenhuma chamada
à conta AWS, merge, deploy, apply ou destroy. Nenhuma PR nova.

## Snapshot de entrada e governança

SHAs abaixo foram consultados antes desta correção documental. O HEAD final de cada
PR muda com a correção; conferir os commits/checks atuais nos links e na entrega final.
Não usar o snapshot abaixo como SHA do novo conteúdo.

| PR existente | HEAD auditado na entrada | CI de entrada | Estado de entrada |
|---|---|---|---|
| [tech-challenge-oficina #3](https://github.com/ppdssdev/tech-challenge-oficina/pull/3) | `ed67cf13c32d3e166dee379789b0e90facb5f52e` | [Aprovado](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41235922944) | blocked / REVIEW_REQUIRED |
| [tech-challenge-oficina-auth-lambda #1](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/1) | `1d3a66524c5486c66c168487c04511286263141a` | [Aprovado](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/actions/runs/41235925327) | blocked / REVIEW_REQUIRED |
| [tech-challenge-oficina-infra-k3s #1](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/pull/1) | `0cf05a52f11bbeca910e8042be6247200ef3aa03` | [Aprovado](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/actions/runs/41235927920) | blocked / REVIEW_REQUIRED |
| [tech-challenge-oficina-infra-rds #1](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/pull/1) | `90dfbb5e8834e1b5f0b9f2d3e18f5b8a98fc7b75` | [Aprovado](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/actions/runs/41235928923) | blocked / REVIEW_REQUIRED |

Diagnóstico conjunto confirmado por REST/GraphQL: `mergeable=true`, PRs abertas e
não draft, checks obrigatórios aprovados, `strict=true`, uma aprovação exigida e
zero reviews registrados; zero threads pendentes e nenhum ruleset adicional listado.
`enforce_admins=true`, `dismiss_stale_reviews=true`, force-push/exclusão proibidos.
Portanto o impedimento identificado é revisão obrigatória ausente, não conflito,
falha CI ou aprovação de deployment. Não há aprovação independente registrada no
GitHub, mesmo que uma revisão técnica externa tenha produzido estes apontamentos.

Consulta de colaboradores: aplicação tem ppdssdev/admin e soat-architecture/write;
Auth, K3s e RDS têm apenas ppdssdev/admin. Não há convites pendentes nos quatro.
Isso atualiza a pendência histórica do relatório de entrega: o avaliador já tem
acesso write na aplicação. Nenhuma concessão de acesso foi feita nesta correção.

**Ação manual do proprietário:** solicitar review formal de pessoa habilitada,
diferente do autor, em cada PR após o último commit. Na aplicação o avaliador já
pode revisar; nos outros repos não há segundo colaborador habilitado listado, então
a organização desse acesso exige decisão administrativa separada do proprietário.
Esta tarefa não adiciona permissões, envia convites ou solicita reviews via API.
Após revisão, verificar checks do HEAD final/base atual e threads resolvidas; novos
commits podem invalidar aprovação. Não reduzir regras nem usar bypass para concluir.
[Regra de aprovação](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/approving-a-pull-request-with-required-reviews).

staging/production existem nos quatro repos. production requer ppdssdev, impede
self-review e bypass administrativo, aceita branches protegidas. Essa aprovação é
de deployment futuro, não de PR; não bloqueia o CI atual porque não há jobs de deploy.
Antes de CD iniciado pelo proprietário, planejar reviewer independente autorizado.

## Confronto dos contratos

| Interface | Produtor → consumidor | Constatação e gate |
|---|---|---|
| JWT | Auth → aplicação | Proposta RS256/JWKS coerente nos READMEs; runtime HMAC incompatível, sub username/roles versus UUID/role. Gate obrigatório de assinatura, identidade, RBAC e migração ADMIN em deployment-architecture.md |
| Cliente elegível | Aplicação/RDS → auth | Proposta comum auth.customer_identity_v1(customer_id, cpf, active), TLS e SELECT read-only; view/grants e consulta não implementados. Schemas não comprovam comportamento |
| HTTP login | API Gateway → Lambda | POST /auth/token, proxy 2.0, erros e TTL 900 s documentados; schema JSON não implementa handler, limite de body, throttling ou roteamento |
| Rede | K3s → RDS/auth | planned_fields coincide com documentação: VPC, subnets privadas, SG app/auth e registry; apenas nomes, sem outputs reais ou inputs tipados consumidores |
| Banco | RDS → app/auth | planned_fields coincide: endpoint/porta/nome e ARNs de segredos; nenhum valor real, usuário SQL ou segredo criado |
| Dependências | rede → banco → migrations/app → auth | Ordem sem ciclo no desenho; K3s não depende do RDS, SG auth nasce na rede, regra 5432 é dona da stack RDS. Sem teste real entre stacks |
| Métricas | App → Prometheus | Runtime permite scrape anônimo; documentação corrigida para separar fato atual de requisito cloud. Gate de isolamento e scrape autorizado ainda aberto |
| Artefatos | CI → auditoria/CD futuro | GITHUB_SHA de PR é merge sintético; HEAD da PR é outro campo. Sem CD e sem release publicada por estes pipelines |

O contrato de campos tem consistência documental, não compatibilidade executável.
Não adicionar consumo de metadados implemented=false como configuração de deploy.

## Matriz objetiva de requisitos e evidências

“Local” abaixo refere-se às validações registradas no bootstrap, salvo o teste
Actuator repetido nesta revisão. Não representa execução AWS. “Pendente” exige
implementação e/ou evidência futura, não se resolve com aprovação de documentação.

| Requisito | Implementado agora | Validado localmente | Pendente de implementação AWS/cloud | Pendente de evidência de deploy |
|---|---|---|---|---|
| Quatro repositórios independentes | Sim, repos reais e PRs | Git/origens e escopos conferidos | Não se aplica | Não se aplica |
| CI independente | Sim, quatro workflows | App 137 testes/cobertura, Docker, 18 manifests; auth 5 testes; Terraform/Checkov | Não se aplica | CI verde não é evidência de deploy |
| Domínio Java/PostgreSQL/Flyway | Sim, app existente | Testcontainers e cobertura aprovados no bootstrap | Conectar ao RDS real e separar credencial de migration/runtime | Integração app/RDS e persistência em cloud |
| Autenticação CPF | Apenas schemas, README e bundle | Formato/erros de contrato; não consulta nem emissão | Handler, view/grants, consulta, assinatura, API Gateway | Login real ativo/inativo/ausente e falhas |
| JWT app ↔ Lambda | HMAC atual; RS256 apenas proposto | Leitura do adapter/filtro; sem teste conjunto RS256 | Implementar compatibilidade antes de integrar | Aceite/recusa, RBAC e rotação com serviço real |
| EC2/K3s/rede/IAM | Somente metadados Terraform | fmt/init sem backend/validate e barreira sem recursos | Toda infraestrutura e configuração K3s | Recursos, workloads, acesso restrito e probes |
| RDS privado/proteção de dados | Somente metadados Terraform | Mesmas validações estáticas; nenhuma regra de recurso AWS testada | Banco, subnets, SG, TLS, criptografia, backups e proteção | Conectividade privada, backup e restauração |
| State remoto/locking/outputs | Apenas desenho | Revisão documental da ordem sem ciclos | S3 locking, roles e publicação segura de outputs | Lock concorrente, segregação e consumo real |
| Observabilidade | Métricas/app e manifests locais; scrape anônimo | Teste Actuator e validação de manifests; sem comprovar cloud | Restringir scrape/exposição, retenção e alarmes | Scrape autorizado, recusa externa, dashboards/alertas |
| CD staging/production | Não; somente desenho e environments GitHub | Ausência de jobs CD conferida | OIDC, roles mínimas, publish/promote e rollout | Deploy automático, aprovação production e smoke |
| Versionamento/promoção/rollback | Bundle de contrato; imagem CI efêmera; desenho de release | Checksum/build de contrato, sem promoção | Registry, proveniência, promoção por digest, aliases/runbooks | Mesmo digest por ambiente e rollback/restauração ensaiados |
| Isolamento e custos | Documento e orçamento preliminar | Revisão documental; conta não consultada | Confirmar elegibilidade/orçamento, limites e isolamento | Evidências por ambiente e acompanhamento de custos |
| Governança e avaliador | Proteções e environments; acesso avaliador só na app | APIs consultadas em modo leitura | Não se aplica | Revisão formal/acesso nos demais repos pendentes; não são deploy |

## Validação desta correção e conclusão

Escopo documental: confronto com código e schemas existentes, outputs/metadados,
workflows e API GitHub. O comando `mvn -B -Dtest=ActuatorSecurityIntegrationTest test` passou nesta revisão:
três testes, zero falhas/erros/skips, incluindo HTTP 200 sem JWT em Prometheus.
Não se alterou código para mudar esse resultado. Os checks das PRs serão
executados novamente após push documental; consultar os HEADs atuais antes de revisar.
As evidências históricas de 137 testes, Docker, 18 manifests e cinco testes auth
permanecem no relatório original, sem alegação de nova infraestrutura.

Para aprovar **o bootstrap**, revisar consistência/limites, confirmar CI do último
commit e obter review habilitado nas quatro PRs. Isso não aprova uso em produção.
Para concluir **a Fase 3**, implementar e comprovar os itens pendentes da matriz:
primeiro compatibilidade auth/app e observabilidade segura; depois, em escopo
separado autorizado e orçado, stacks reais, deploy staging, CD/OIDC, proteção
production, testes ponta a ponta, rollback e evidências do edital.

Riscos residuais: CPF isolado não prova identidade, revogação não imediata, HMAC
incompatível com RS256 proposto, scrape anônimo, Single-AZ/single-node sem HA,
custo/elegibilidade Free Plan não confirmados, interfaces Terraform ainda não
executáveis e falta de reviewer habilitado em três repos. Nenhum desses riscos
é eliminado por um Checkov sem recursos ou por CI verde.
