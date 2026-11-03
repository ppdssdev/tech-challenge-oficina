# Evidência operacional sanitizada — 2026-11-03

Janela da coleta: aproximadamente **12:59–13:08 UTC**, staging `us-east-2`.
Consultas AWS autenticadas com perfil local `oficina-staging`; identidade da conta
conferida antes da coleta. Não foram lidos valores de Secrets Manager, Secrets
Kubernetes, dados pessoais, state ou variáveis de ambiente da Lambda. Não houve
apply, deploy, teste de carga, invocação Lambda, acesso SQL remoto ou alteração de
recursos. SSM Run Command executou exclusivamente `kubectl get` e `kubectl top`.

## Resultados observados

| Consulta | Resultado | Limite da prova |
|---|---|---|
| EC2 describe-instances (tag oficina-staging-k3s) | running, m7i-flex.large, IMDSv2 required, hop limit 1 | Não prova isolamento IMDS de pods/reboot/restore |
| SSM describe-instance-information | Online, agente 3.3.4793.0 | Sessão interativa/túnel não exercitados |
| kubectl get nodes | Um nó Ready, v1.34.6+k3s1 | Não oferece HA de nó |
| kubectl get deployments -A | API 3 Ready/3 desejados; CoreDNS, local-path e Metrics Server 1/1 | Readiness não substitui fluxo de negócio |
| kubectl get hpa | oficina-api-hpa; 1–3; CPU 4%/70%; memória 75%/75%; 3 réplicas | Amostra pontual, não ensaio de escala sob carga |
| kubectl top nodes | CPU 42m/2%; memória 1691Mi/24% | Amostra pontual, sem série histórica |
| kubectl get services/ingress -A | API ClusterIP 8080; nenhum Ingress | Não é teste externo de todas as superfícies |
| kubectl get pods/statefulsets/daemonsets -A | Só API e componentes K3s descritos; sem Prometheus/Grafana/coletor tracing | Não exclui serviço externo fora do cluster |
| kubectl get jobs -n oficina-staging | Nenhum Job presente | Não prova ausência de execuções anteriores Flyway |
| RDS describe-db-instances | available, PostgreSQL 16.15, db.t4g.micro, oficina_db, privado, criptografado, Single-AZ | Não houve SQL remoto ou restore |
| RDS backup/deletion metadata | retenção 1 dia; deletion protection true; subnet group AZ a/b | Configuração de backup não comprova restauração |
| SGs da VPC | app/auth sem ingressos; PostgreSQL 5432 só app/auth; endpoint 443 só app/auth | Rotas/NACLs/egress efetivo não foram auditados integralmente |
| EC2 describe-vpc-endpoints | Secrets Manager Interface available, private DNS true, duas subnets | Não foi lido segredo nem testada negação por principal |
| ECR describe-repositories | oficina-staging-api IMMUTABLE, KMS, scanOnPush true | Não comprova resultado limpo de scan/proveniência do digest |
| Lambda get-function-configuration | Active, Python 3.13, 256 MiB, timeout 10s, mesma VPC do nó | Código implantado não foi baixado/comparado ao SHA Git |
| Lambda get-function-concurrency | ReservedConcurrentExecutions = 0 | Invocações estão bloqueadas; E2E atual não demonstrado |
| API Gateway get-apis/get-routes | HTTP API oficina-staging-auth; DisableExecuteApiEndpoint=true; POST /auth/token AWS_IAM | Endpoint fechado, não uma API Gateway privada do tipo PRIVATE |
| IAM ListOpenIDConnectProviders | AccessDenied com explicit deny em service control policy | Prova do bloqueio de auditoria; não prova inexistência de provider nem teste STS OIDC |

Imagem da API observada (somente digest, conta/hostname omitidos):
`sha256:40b333a1546a157438789945b6bc47d7eea5c59e8958ba9dd31176beb8686527`.
Código Lambda observado (CodeSha256, não é credencial):
`6U9dtUjtBtrqWf9Xpf9VgtrZcD+solXr0xDzHwfaZ4E=`; LastModified
`2026-11-03T02:00:42.000+0000`. Não foi estabelecida correspondência entre esse
artefato implantado e o commit Lambda auditado.

Referências SSM para consulta pelo operador na conta, sem endereços internos:
`41f25984-f57d-4339-a238-422e6d463f3c` (runtime) e
`46959ca5-3b88-4d0d-a6dc-b06bf5dafd99` (observabilidade), ambas Success.
A primeira retornou “No resources found” para inventário vazio, sem falha de comando.
Este arquivo transcreve resultados sanitizados; não é exportação assinada do CloudTrail.

## Reprodução somente leitura

Definir os identificadores do ambiente por canal autorizado; não colar resultados
brutos contendo endpoints, IPs, credenciais ou dados pessoais na PR.

```bash
aws --profile oficina-staging --region us-east-2 sts get-caller-identity
aws --profile oficina-staging --region us-east-2 rds describe-db-instances   --db-instance-identifier oficina-staging-postgres   --query 'DBInstances[].{Status:DBInstanceStatus,Engine:EngineVersion,Public:PubliclyAccessible,MultiAZ:MultiAZ,Encrypted:StorageEncrypted,Retention:BackupRetentionPeriod}'
aws --profile oficina-staging --region us-east-2 lambda get-function-concurrency   --function-name oficina-staging-auth
aws --profile oficina-staging --region us-east-2 apigatewayv2 get-apis   --query 'Items[].{Name:Name,Disabled:DisableExecuteApiEndpoint,Type:ProtocolType}'
aws --profile oficina-staging --region us-east-2 ecr describe-repositories   --repository-names oficina-staging-api   --query 'repositories[].{Name:repositoryName,Mutability:imageTagMutability,Scan:imageScanningConfiguration.scanOnPush}'
# Na sessão SSM autorizada do nó; comandos não leem Secrets:
sudo k3s kubectl get deployments -A
sudo k3s kubectl -n oficina-staging get hpa
sudo k3s kubectl get pods -A
sudo k3s kubectl get ingress -A
sudo k3s kubectl top nodes
```

Não executar o fluxo de autenticação alterando concorrência como parte desta
reprodução. Para fechar E2E, o operador precisa de janela sintética controlada,
artefato/chaves revisados e testes de ownership/negação/rotação. O relato anterior
em [consolidação](../staging/consolidation.md) afirma sucesso de CUSTOMER e ADMIN,
mas não registra todo esse aceite e não foi revalidado aqui.
