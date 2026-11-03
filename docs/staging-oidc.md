# Staging GitHub OIDC — Etapa 2

## Auditoria (2026-11-03)
Quatro mains remotas inspecionadas: CI de PR/push não solicita token OIDC nem usa credenciais AWS; app também tem smoke manual Kind. Roots k3s/RDS fixam Terraform 1.14.6 e AWS 6.38.0. RDS fixava profile oficina-staging; removido para usar cadeia padrão. Local: export AWS_PROFILE=oficina-staging. CI: configure-aws-credentials exporta credenciais STS e session token; não definir AWS_PROFILE. Backend e provider possuem cadeias independentes; ambos devem usar a mesma identidade temporária.

AWS list-roles retornou roles de serviço, administração, oficina-staging-node (EC2) e oficina-staging-auth-execution (Lambda), nenhuma trust GitHub. ListOpenIDConnectProviders e GetOpenIDConnectProvider foram negados explicitamente pela SCP p-ejzb01rz da organização o-jfc8ijl55k. Existência/provider audiences permanecem NÃO VERIFICADOS. Solicitar ao administrador da organização auditoria/leitura autorizada; não tentar contornar SCP. Não houve alteração AWS.

## Recursos e limites
Root infra-k3s, mesmo backend S3, gerencia quatro roles oficina-staging-github-{app,k3s,rds,lambda}. Default referencia provider existente por ARN exato, sem duplicação; se ausente o plan falha. manage_github_oidc_provider=true só após comprovar ausência ou aprovar adoção de provider fora de outro state. Provider gerenciado tem prevent_destroy. Conferir client_id_list contém sts.amazonaws.com.

Se existente e gerenciado em outro state, manter referência. Se existente sem dono, opção futura explicitamente autorizada: habilitar manage_github_oidc_provider e importar no endereço aws_iam_openid_connect_provider.github[0], ID arn:aws:iam::797781632267:oidc-provider/token.actions.githubusercontent.com; revisar plan sem recriação. Nenhuma importação executada nesta etapa.

Trust StringEquals exige aud sts.amazonaws.com e sub repo:ppdssdev/REPOSITORIO:ref:refs/heads/main. Fork, pull_request, tag, outra branch/repo/audience não correspondem. Nenhum environment no job: adicionar environment muda sub e exige nova revisão. Proteger main contra escrita direta e revisar alterações de workflows. Qualquer workflow executando em main pode usar essa trust; não há isolamento por nome do workflow.

Roles de validação não têm identity policies ou attachments: GetCallerIdentity não necessita Allow. Não conceder AdministratorAccess, PassRole, deploy, secrets, S3 ou KMS nesta etapa. CD fica na Etapa 3.

| Role | State futuro (sem acesso nesta etapa) |
|---|---|
| app | nenhum |
| k3s | staging/k3s/terraform.tfstate |
| rds | staging/rds/terraform.tfstate |
| lambda | staging/lambda/terraform.tfstate |

Bucket oficina-staging-tfstate-797781632267. Na Etapa 3, revisar política por role: ListBucket somente com s3:prefix da chave exata; GetObject no state próprio; PutObject apenas se houver escrita autorizada; GetObject/PutObject/DeleteObject somente no lockfile da chave acrescida de .tflock. Nunca DeleteObject no state, prefixo staging/* ou acesso cruzado implícito. Remote-state adicional exige justificativa e GetObject exato. Se SSE-KMS for encontrado, revisar chave e encryption context específicos; não adicionar kms:* preventivamente. Nenhuma role desta etapa executa plan remoto.

## Revisão e comandos (operador local autorizado)
Usar variáveis staging existentes revisadas; não inventar AMI/owner nem desativar guards.

```sh
export AWS_PROFILE=oficina-staging
aws sts get-caller-identity
# Confirmar Account 797781632267 antes de prosseguir.
terraform init -input=false -lockfile=readonly
terraform fmt -check -recursive
terraform validate
terraform plan -input=false -var-file=/CAMINHO/SEGURO/staging.tfvars -out=/tmp/staging-oidc.tfplan
terraform show /tmp/staging-oidc.tfplan
```

Executar no root k3s; repetir no RDS para confirmar ausência de mudanças de recursos após remover profile. Plan pode falhar pela SCP e não foi executado contra AWS. Não usar -target para ocultar drift. Plans podem conter dados sensíveis: não publicar artefatos. Esperado k3s: quatro roles adicionadas e zero mudanças em compute/rede; provider adicional somente com criação explicitamente habilitada e ausência comprovada. RDS: zero mudanças de recursos.

Após revisão/merge e provisionamento manual fora desta tarefa, disparar Validate staging OIDC em main em cada repo. Evidência: STS Account 797781632267, ARN assumed-role/oficina-staging-github-<role>/github-<run>-<attempt>, eventos CloudTrail AssumeRoleWithWebIdentity com sujeito correto. Não imprimir JWT/credenciais. Branch diferente deve pular job; tokens fork/PR/outro aud devem ser recusados pela trust. Simulação IAM não substitui teste real de federação. Verificar também negação de acesso aos três states e lockfiles com sessões destas roles, sem escrever/deletar objetos.

Riscos pendentes: SCP pode impedir leitura/criação/federação; provider existente e suas audiences não confirmados; proteção de main não auditada; políticas de recurso externas podem conceder acessos e precisam revisão antes de ampliar uso. Nenhum apply, destroy, import, merge ou CD foi executado.

Referências: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html ; https://registry.terraform.io/providers/hashicorp/aws/latest/docs ; https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws
