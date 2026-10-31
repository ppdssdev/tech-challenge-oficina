# Dependências AWS, state e custos

Região proposta: us-east-2. Nenhuma chamada à conta AWS, plan, apply, destroy,
secret, access key ou recurso AWS foi criado nesta etapa. Valores abaixo são orçamento
preliminar, não cotação nem evidência de elegibilidade no Free Plan.

## Ordem futura sem ciclos

1. Bootstrap administrativo aprovado: bucket S3 state privado, versionado, criptografado,
   bloqueio público; provedor OIDC e roles restritas. Ainda não implementado; manter
   futuro código bootstrap no repo infra-k3s em root independente, state separado.
2. infra-k3s: rede, subnets privadas em duas AZs, EC2/K3s, SG base app/auth, registry.
3. infra-rds: consome vpc_id/private_subnet_ids/app_security_group_id/auth_security_group_id;
   cria SG de banco com entrada 5432 só dos dois SGs, RDS e referências de credenciais.
4. Aplicação: migrações Flyway, view auth.customer_identity_v1 e grants, deploy API.
5. Auth: consome rede/SG auth e endpoint/segredo read-only; provisiona Lambda, endpoint
   Secrets Manager, HTTP API e publicação JWKS. API deve suportar RS256 antes de liberar login.
6. Observabilidade e testes ponta a ponta; depois avaliar promoção production.

K3s não consome outputs RDS no Terraform: conexão é fornecida no deploy da aplicação.
RDS não depende da função Lambda: SG auth preexiste na rede. Auth não altera SG do
RDS; regras pertencem exclusivamente à stack RDS. Cada recurso tem um único dono.

## Interfaces versionadas

| Produtor | Outputs futuros sem segredo | Consumidor |
|---|---|---|
| K3s | vpc_id, private_subnet_ids, app_security_group_id, auth_security_group_id, image_repository_url | RDS, auth, deploy app |
| RDS | db_endpoint, db_port, db_name, app_secret_arn, auth_secret_arn | deploy app, auth |
| Auth | issuer, audience, jwks_uri, api_base_url, function_alias | configuração app e testes |

As duas stacks atuais expõem apenas `integration_contract` com implemented=false e
nomes previstos. Não existem endpoints ou ARNs reais. Publicação futura por SSM
Parameter Store, caminhos `/oficina/<environment>/<stack>/v1/...`, acesso por role
consumidora limitado ao prefixo. Secret ARN é referência; conteúdo nunca é output.
Não conceder leitura completa de state a consumidores via terraform_remote_state.
Registrar versão do contrato e commit produtor; validar schema e ambiente antes do deploy.

State remoto futuro: S3 com key `<environment>/<stack>/terraform.tfstate`,
`encrypt=true`, `use_lockfile=true` (Terraform >=1.10), versionamento e acesso mínimo.
Separar bootstrap/k3s/rds/auth e ambientes; prefixos e roles distintas, não depender
somente de workspaces. Lock .tflock com Get/Put/Delete específico; state com Get/Put,
sem Delete rotineiro. Nunca desabilitar locking; recovery de lock somente após
verificação humana de ausência de execução. Não usar DynamoDB novo para locking.
Atualmente init -backend=false usa somente validação local sem backend remoto.
[Referência HashiCorp](https://developer.hashicorp.com/terraform/language/backend/s3).

## Restrições e orçamento antes de provisionar

Free Plan tem duração/créditos limitados; não assumir 750 h gratuitas nem elegibilidade
de todas as classes. Confirmar saldo, serviços e expiração com proprietário **em tarefa
futura autorizada**, sem upgrade de plano. Interromper antes de provisionar se requisitos
exigirem Paid Plan. Fontes oficiais consultadas em 2026-10-31:
[Free Plan](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-FAQ.html),
[EC2](https://aws.amazon.com/ec2/pricing/on-demand/),
[RDS PostgreSQL](https://aws.amazon.com/rds/postgresql/pricing/),
[IPv4](https://aws.amazon.com/vpc/pricing/).

Estimativa de planejamento em USD/mês, 730 h, Linux on-demand, um ambiente pequeno,
sem descontos/créditos/impostos; valores por categoria são margens a confirmar no
Pricing Calculator us-east-2 antes de autorizar. Não garantem disponibilidade no Free Plan.

| Item / hipótese | Reserva mensal estimada |
|---|---:|
| Uma EC2 t3.small para K3s | 16–22 |
| EBS gp3 20–30 GiB | 2–4 |
| Um IPv4 público (0,005 USD/h x 730) | 3,65 |
| RDS PostgreSQL micro Single-AZ, armazenamento 20 GiB | 15–25 |
| Endpoint Secrets Manager interface em 1–2 AZs | 8–16 |
| Secrets, S3 state/JWKS, registry, DNS, logs e baixo tráfego auth | 3–12 |
| **Total aproximado por ambiente** | **48–83** |

Dois ambientes permanentes: aproximadamente 96–166 USD/mês, antes de tráfego extra,
CPU burst, snapshots excedentes e retenção. Egress, logs e backups podem ultrapassar
a reserva; orçamento não é teto automático. Sem EKS/NAT Gateway/ALB no desenho inicial.
Antes de criar: estimativa detalhada exportada, aprovação explícita, alertas de orçamento
50/80/100%, limites de retenção, agenda de desligamento e plano de limpeza aprovado.
Parar RDS/EC2 não elimina armazenamento, snapshots, endpoints e IPv4; considerar
reinício automático RDS e limites de parada. Não agendar destroy automático de banco.

RDS futuro: criptografia, acesso privado, backups >=7 dias, deletion protection,
snapshot final obrigatório, limite de autoscaling storage e teste de restauração.
Estas são exigências para a próxima implementação, não controles já implantados.

## Limite das interfaces neste bootstrap

`planned_fields` nas duas stacks concorda com a tabela acima, mas contém somente
nomes. Não há outputs individuais com valores reais, variáveis tipadas de VPC/subnets
na stack RDS, validação de compatibilidade entre stacks ou parâmetros SSM publicados.
`integration_contract.schema_version=1` versiona metadados de intenção e
`implemented=false` deve impedir tratá-los como configuração consumível em deploy.
A próxima implementação deve definir tipos, validar ambiente/versão e testar
produtor e consumidor conjuntamente. Não inferir endpoint, ARN ou conectividade de
um `terraform validate` aprovado. A view/grants auth permanecem pendentes de migração
na aplicação e de testes PostgreSQL; não foram adicionados nesta revisão.
