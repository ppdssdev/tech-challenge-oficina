# Matriz de evidências — Fase 3

Grupo 97 · Pedro Paulo dos Santos Silva · Discord pedropaulo6447.
Referência de requisitos: onze entregáveis da solicitação e edital **13SOAT - Fase 3 -
Tech Challenge.pdf**, localizado em Downloads e lido integralmente. SHA-256:
`6604c5a705315f647ebf8b199df8bd2892c371265d4a4fa25082ab2122b181c4`.
O PDF original não foi republicado; páginas 2–5 (numeração física incluindo capa)
contêm requisitos/entrega. Os identificadores F3 abaixo são internos de
rastreabilidade, não números oficiais FIAP nem declaração de nota/aprovação.

**D**: documento/código verificado; **O**: observado em staging em 2026-11-03;
**CI**: run remoto success consultado; **P**: pendente; **R**: relato anterior, sem
reexecução atual. Um requisito pode ter prova D/CI e continuar operacionalmente P.
Comandos abaixo são reprodução, não lista de operações executadas nesta tarefa.
Terraform sempre sem backend nesta validação; nenhum apply/state remoto.

| Requisito FIAP mapeado | Implementação/artefato | Status e alcance | Comando de validação | Fonte |
|---|---|---|---|---|
| F3-01 Componentes AWS | EC2/K3s, Java, ECR, RDS, Lambda, Gateway, IAM, Secrets Manager | D/O; sem tracing e sem Prometheus/Grafana cloud | `aws ec2 describe-instances`; `aws rds describe-db-instances`; consultas sanitizadas do registro | [Diagrama](deployment-architecture.md), [coleta](operational-evidence.md) |
| F3-02 Implantação/rede | Subnets, SGs, ClusterIP e SSM separados de telemetria | D/O parcial; rotas/NACLs/TLS efetivo P | `kubectl get services,ingress -A`; `aws ec2 describe-security-groups` | [Implantação](deployment-architecture.md), [network.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/10fa81e88e8e5cd9ab06fe8661cf1a735be26675/network.tf) |
| F3-03 Autenticação | CPF → V2 → RS256 → Java; contrato HTTP e janela sintética | D/CI; Lambda concorrência zero; E2E cloud R/P | `bash scripts/validate_external.sh ../tech-challenge-oficina` no repo auth; conferir concorrência sem ativá-la | [Sequência](sequences.md), [auth/postgres.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/postgres.py), [auth/jwt.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/jwt.py) |
| F3-04 OS | Abertura, diagnóstico, itens, aprovação/recusa, estoque, finalização/entrega | D/CI; fluxo cloud atual P | `mvn -B clean verify` | [Sequência](sequences.md), [casos de uso](../../src/main/java/br/com/fiap/techchallenge/oficina/application/usecase) |
| F3-05 MER Flyway | 12 tabelas/2 views; 116 colunas | D; SQL aplicado em PostgreSQL 16.15 descartável, sem RDS | Aplicar V1–V11 numericamente; consultar catálogo conforme modelo | [MER/dicionário/hashes](data-model.md) |
| F3-06 PostgreSQL/constraints/histórico | RDS + constraints reais + histórico transacional + roles + análise de índices | D/O metadados; grants RDS/benchmark/restore P | `mvn -B clean verify`; `SELECT ... FROM pg_constraint`; consultas de privilégios com login real em janela própria | [Justificativa](database-design.md), [grants](../../scripts/staging/02-runtime-grants.sql) |
| F3-07 RFCs | AWS/K3s, DB, JWT, Actions/OIDC, observabilidade | D; observabilidade cloud/OIDC como propostas | Parser Mermaid e verificação de links conforme relatório | [Cinco RFCs](decisions.md) |
| F3-08 ADRs | K3s/EKS, RDS privado, Lambda, HPA, Terraform separado, digest | D; estados individualizados | Comparar fontes e estado com cada ADR | [Índice ADRs](decisions.md) |
| F3-09 Trade-offs | Falha única, Gateway fechado, CPF sintético, SCP/CD, produção e custos | D/O parcial; custo medido/produção P | Inventário sanitizado; consulta autorizada de Billing fora desta auditoria | [Limitações](trade-offs.md), [deny SCP](operational-evidence.md) |
| F3-10 READMEs | Quatro repos, execução/testes/segurança/CI/diagramas/deploy/contratos | D; links cross-repo novos disponíveis após merges | Validar links locais e escopo de diff | [Repos](repositories.md) |
| F3-11 Evidências | Baseline imutável, runs e coleta sanitizada | D/O/CI conforme cada linha; sem equivaler a certificação | Reproduzir comandos desta matriz com contexto correto | Este arquivo, [baseline](README.md) |
| CI API | Maven, Docker e validação infra | CI success no SHA auditado; não reexecutado como parte do patch documental | `mvn -B clean verify`; `python3 scripts/staging/test-render.py` | [Run API](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41636042180) |
| CI Lambda | Validação integral sem skips exigida pelo script | CI success no SHA auditado; não comprova artefato cloud | `bash scripts/validate_external.sh ../tech-challenge-oficina` | [Run auth](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/actions/runs/41625965174) |
| CI K3s | fmt/validate/política/testes/Checkov/TFLint | CI success no SHA auditado | `bash scripts/preflight.sh` (local, sem AWS); TFLint conforme workflow | [Run K3s](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/actions/runs/41625905236) |
| CI RDS | fmt/validate/política/Checkov | CI success no SHA auditado | `terraform init -backend=false`; `terraform validate`; `python scripts/bootstrap_policy.py` | [Run RDS](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/actions/runs/41625940842) |
| HPA | Metrics Server e autoscaling/v2 | O: 3 réplicas; ensaio de subida/descida P | `kubectl -n oficina-staging get hpa`; `kubectl top nodes` | [Manifest](../../k8s/staging/hpa.yaml), [coleta](operational-evidence.md) |
| Métricas/dashboards | Actuator/Micrometer, Compose/Kind Prometheus/Grafana | D local; cloud P/ausente no inventário | `docker compose up -d --build`; consultar targets/dashboards locais | [Compose](../../docker-compose.yml), [observability](../../observability) |
| Tracing | Nenhum exportador/coletor comprovado no baseline ou inventário | P, não implementado/comprovado | `rg -i 'opentelemetry|zipkin|jaeger|tempo' pom.xml src/main/resources k8s observability` (sem correspondências de implementação) | [RFC 0005](rfc/0005-observability.md) |
| OIDC/CD | PRs preparatórias; SCP impede auditoria provider | P e bloqueio O observado; nenhum CD main | `aws iam list-open-id-connect-providers` (negação observada); STS WebIdentity ainda não testado | [CI/CD](cicd-strategy.md) |
| Produção | Sem ambiente implantado demonstrado | P/fora do staging observado | Inventário com escopo production e evidência de release/SLO em tarefa própria | [Trade-offs](trade-offs.md) |
| Segurança documental | Links, Mermaid, modelo e conteúdo novo revisados | Resultado no relatório de validação | `git diff --check` e parser/scanner descritos | [Relatório](documentation-validation.md) |

## Confronto adicional com o edital oficial

Esta tabela complementa os onze entregáveis documentais: documentação completa
não transforma implementação parcial em atendimento integral do desafio.

| Exigência do edital | Situação verificável | Validação/fonte |
|---|---|---|
| Gateway e função CPF consultando existência/status e JWT | Recursos e código implementados; endpoint fechado, concorrência zero; CPF sintético e habilitação V2 independente da atividade de negócio | Metadados e sequência; E2E real e interpretação de status pendentes |
| Quatro repos com CI/CD e deploy automático de homologação/produção | Quatro CIs comprovados; CD automático não atendido; ausência de produção demonstrada | Runs e PRs em [CI/CD](cicd-strategy.md); executar futura pipeline real, não release manual |
| Main protegida, sem commits diretos, PR obrigatório | Configuração de PR/checks e enforce_admins consultada; nenhum push proibido testado; zero reviews humanos obrigatórios | [Governança e comandos](governance-audit.md) |
| Kubernetes escalável, Terraform, DB gerenciado | D/O: K3s/HPA/RDS; single-node/Single-AZ sem HA real | Inventário runtime; ensaio HPA/recuperação pendente |
| Latência, CPU/memória, healthchecks/uptime | Actuator e dashboard local; Metrics Server no cluster; sem histórico cloud comprovado | [RFC observabilidade](rfc/0005-observability.md); consultar targets/séries após implantação |
| Alertas para falhas de OS | Não comprovados; painel de outbox falha não é alerta testado | Configuração local observability; futuro teste de regra/entrega e recuperação |
| Logs JSON com correlação entre requisições | Parcial: traceId de erros; logback textual, sem correlação global/JSON comprovados | [logback](../../src/main/resources/logback-spring.xml), [handler](../../src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web/ApiExceptionHandler.java) |
| Dashboards de volume diário de OS, tempo por status e erros de integrações | Dashboard local de HTTP/JVM/outbox; conjunto específico exigido não completo e cloud ausente | [dashboards](../../observability/grafana/dashboards); não equiparar média global de execução a tempo por status |
| Documentação de componentes, sequências, RFCs/ADRs e MER/justificativa | Entregue nas PRs documentais; validação de links/Mermaid/modelo concluída | Índice e relatório documental |
| READMEs, Dockerfiles quando aplicável, contratos e deploys ativos | READMEs/contratos presentes; Dockerfile Java; Lambda usa ZIP/runtime gerenciado; infra não exige Dockerfile de aplicação | READMEs e baseline; não há Swagger staging público |
| Vídeo até 15 min: autenticação, pipeline/deploy automático, dashboards/logs/traces | Vídeo específico desta Fase 3 não comprovado; traces e CD não atendidos não podem ser simulados como reais | Produzir demonstração fiel e registrar URL/duração; vídeo antigo não certifica esta fase |
| PDF único com links e banca em todos os repos | PDF final desta fase não produzido nesta consolidação Markdown; banca presente somente no Java | [Governança](governance-audit.md); concluir submissão pelo proprietário |

## Pendências para fechar comprovação operacional

1. Vincular digest API e CodeSha256 Lambda aos SHAs/builds aprovados e ao ZIP assinado;
   confirmar code signing efetivo e não somente Terraform Enforce.
2. Em janela sintética autorizada, executar Lambda V2 → JWT → OS própria (200), OS
   alheia (404/403 conforme rota), ADMIN negado a CUSTOMER, tokens inválidos/expirados,
   cliente inativo/ambíguo/fora da allowlist, rotação JWKS/PEM e falhas de dependências.
3. Consultar versão/checksums Flyway e grants efetivos no RDS, incluindo negação a
   V1/tabelas-base/DDL, TLS verify-full e papéis independentes. Não publicar dados.
4. Exercitar HPA com carga, retorno ao patamar inicial e impacto de memória/limites;
   disponibilidade e recuperação do nó; bloqueio IMDS dentro dos pods.
5. Implantar/validar observabilidade cloud se exigida: targets, dashboards, alertas,
   retenção e acesso restrito. Tracing continua lacuna até implementação demonstrada.
6. Resolver auditoria SCP/OIDC e corrigir subjects imutáveis incompatíveis com a trust
   proposta; testar federação, mínimo privilégio e depois CD.
   Quatro PRs preparatórias não equivalem a deploy automatizado funcional.
7. Testar restore RDS/volumes K3s, registrar RPO/RTO, revisar custos atuais/Billing e
   backend remoto (versionamento, lock, acesso). Não ler/publicar state para isso.
8. Adicionar a banca aos três repos restantes, revisar governança conforme decisão
   do proprietário e concluir vídeo/PDF único de entrega. Documentar as lacunas
   exigidas pelo edital; não apresentar staging como produção nem traceId como tracing.
