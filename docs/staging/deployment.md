# Preparação staging — Prompt 8

A validação da reconciliação está no [relatório](../fase3/reconciliation.md).

Este é um procedimento para revisão e execução futura autorizada. Não houve
provisionamento AWS, execução Terraform, aplicação em cluster nem alteração dos
repositórios irmãos. Recursos externos abaixo ainda precisam ser criados.

## Banco e fronteiras

O banco canônico será **oficina_db**. No repositório irmão infra-rds, a alteração
pendente em `rds.tf`, atributo `aws_db_instance.postgres.db_name`, é trocar
`"oficina"` por `"oficina_db"` **antes do primeiro provisionamento**. O endpoint vem
do output `db_endpoint`, sem substituir por IP, alias de Service ou hostname inventado.
Se um banco já existir futuramente, não aplicar mudança destrutiva de nome: planejar
migração de dados e recuperação separadamente.

K3s e RDS continuam com states independentes. RDS permanece privado; a futura regra
5432 deve usar os SGs previstos de K3s/API e Lambda, sem liberar internet. Os outputs
da rede e do banco devem passar por revisão antes de entrar na configuração.

`k8s/local` mantém PostgreSQL e Mailpit, initContainer e fluxos locais. Seu ConfigMap
agora declara explicitamente o perfil `local`. Os defaults de desenvolvimento
ficam em `application-local.yml`, ativado explicitamente com
`SPRING_PROFILES_ACTIVE=local`. Não há perfil local default, preservando o
endurecimento da main. Os manifests staging fixam `SPRING_PROFILES_ACTIVE=staging`, e o validador
recusa combiná-lo com `local`.

## Perfis e configuração obrigatória

`application-staging.yml` desliga Flyway e mantém Hibernate `ddl-auto=validate`.
`StagingConfiguration` rejeita falta de credenciais, segredo HMAC local/curto,
senha administrativa local/curta, usuário de banco diferente de `oficina_api`, DDL
no runtime, URL de banco errado e TLS inseguro. Secrets de staging não têm fallback.

A outbox automática fica **desabilitada em staging** nesta preparação; não há Mailpit
nem SMTP staging provisionado. Habilitar notificações reais exigirá configurar e
validar SMTP em etapa própria. Isso não altera a configuração local.

| Objeto externo no namespace oficina-staging | Conteúdo obrigatório | Consumidor |
| --- | --- | --- |
| ConfigMap `oficina-staging-db` | `jdbc-url` | API e Job |
| ConfigMap `oficina-rds-ca` | `rds-ca.pem`, bundle CA confiável | API e Job |
| ConfigMap `oficina-customer-jwt` | `customer-jwt.yml`, PEMs públicos, `jwks-uri`, `issuer`, `audience` | API |
| Secret `oficina-runtime` | `username=oficina_api`, `password` | somente API |
| Secret `oficina-application` | `hmac-secret`, `admin-username`, `admin-password` | somente API |
| Secret `oficina-migration` | `username=oficina_migrator`, `password` | somente Job |

O segredo HMAC deve ter entropia criptográfica e pelo menos 32 bytes; a senha inicial
ADMIN deve ser única, aleatória e ter pelo menos 16 caracteres. São credenciais da
aplicação, diferentes de roles PostgreSQL. O seed ADMIN existente só cria a conta se
ela não existe; mudar o Secret posteriormente **não troca a senha persistida**.
Planejar rotação administrativa separadamente.

Configuração JDBC, substituindo pelo hostname real fornecido pelo RDS:

```text
jdbc:postgresql://<db_endpoint>:5432/oficina_db?sslmode=verify-full&sslrootcert=/config/ca/rds-ca.pem&connectTimeout=10&socketTimeout=120
```

O guard aceita apenas o hostname de RDS, banco correto e os parâmetros listados;
recusa parâmetros alternativos como `sslfactory`, repetição de sslmode, IPs e aliases.
O bundle precisa ser arquivo absoluto, legível e com certificados X.509. Obter a CA
RDS por canal oficial confiável, verificar origem/integridade e montar read-only.
O driver PostgreSQL verifica cadeia e hostname. Não usar `require`, `prefer`,
`NonValidatingFactory` ou `sslmode=disable`. Planejar atualização do bundle antes de
rotações RDS e testar o novo certificado. O guard não substitui a verificação TLS
do driver nem comprova sozinho a autenticidade do bundle fornecido pelo operador.

Exemplo de `customer-jwt.yml`, somente para demonstração com identidades sintéticas:

```yaml
app:
  jwt:
    customer:
      enabled: true
      issuer: https://synthetic-issuer.invalid
      audience: oficina-staging-demo
      public-keys:
        "[staging-demo]": /config/jwt/staging-demo-public.pem
```

Issuer/audience/kid devem coincidir exatamente com o emissor controlado. Para staging
sem demonstração PEM, fornecer `enabled: false` nesse arquivo. A cadeia JWKS
`/api/v1/customer/**` continua exigindo configuração própria em staging:
`CUSTOMER_JWKS_URI`, `CUSTOMER_JWT_ISSUER`, `CUSTOMER_JWT_AUDIENCE` são injetados das
chaves `jwks-uri`, `issuer`, `audience` do mesmo ConfigMap. URI de JWKS e issuer
devem ser HTTPS confiáveis. Desabilitar PEM não desabilita JWKS. Nenhuma chave privada é
montada na API. A [estratégia de rotação RS256](../security/jwt-rs256-stage-7.md) continua
válida. Não publicar autenticação somente por CPF antes de fator adicional.

## Bootstrap e privilégios PostgreSQL

Executar de um ambiente autorizado com alcance privado ao RDS, usando psql com
`PGDATABASE=oficina_db`, hostname real, `PGSSLMODE=verify-full` e `PGSSLROOTCERT` correto.
Usar senha via mecanismo seguro ou prompt, nunca argumentos, shell history ou Git.
A conta RDS `oficina_admin` e seu segredo gerenciado são **somente para bootstrap**.

Ordem reproduzível, com `psql -v ON_ERROR_STOP=1`:

1. Como administrador RDS, executar `scripts/staging/01-bootstrap-roles.sql`.
   Script transacional, uma vez, falha se roles já existem ou banco é errado.
   Cria os LOGINs **sem senhas**, portanto sem autenticação por senha utilizável.
   Remove CREATE público e CONNECT/TEMP de PUBLIC somente no banco dedicado.
2. Definir senhas independentes geradas por mecanismo seguro para cada LOGIN usando
   `\password oficina_migrator`, `\password oficina_api` e `\password oficina_lambda`,
   ou ferramenta de provisionamento aprovada. Não há senha fixa nos scripts.
   Distribuir cada segredo exclusivamente ao consumidor correspondente.
3. Executar e aguardar o Job Flyway como `oficina_migrator`, que é proprietário dos
   objetos V1–V11. Tem CONNECT, CREATE no banco e USAGE/CREATE em public, mas não
   CREATEROLE, SUPERUSER, CREATEDB nem BYPASSRLS. A API não herda essa role.
4. Como administrador bootstrap, executar `scripts/bootstrap-auth-reader.sql`.
   O script original exige CREATEROLE e capacidade de conceder privilégios da view;
   **não executá-lo como migrator**, que não tem CREATEROLE. O passo 1 concedeu ao
   administrador membership no migrator para acesso aos objetos que ele criou.
5. Como administrador bootstrap, executar `scripts/staging/02-runtime-grants.sql`.
   A API recebe SELECT/INSERT em app_users (login/seed) e DML explícito nas tabelas de
   negócio/outbox/idempotência e SELECT/INSERT no histórico de OS da V9. Não recebe DDL, TRUNCATE, histórico Flyway ou acesso a
   `auth.customer_access`. Novas tabelas futuras exigem grants revisados, sem ALL
   TABLES nem default privileges genéricos.
6. O LOGIN `oficina_lambda` herda **somente** a role NOLOGIN `oficina_auth_reader` do
   contrato estrito v2. Ela permite USAGE em auth e SELECT em
   `auth.customer_identity_v2`, sem acesso à v1 nem às tabelas-base. A Lambda deverá
   ser configurada explicitamente para consultar v2; consumidores v1 existentes
   continuam com `auth_readonly` e seu bootstrap original.
   A conexão Python também deverá usar hostname RDS e `sslmode=verify-full` com CA.
7. Encerrar a sessão privilegiada. A credencial administrativa RDS não deve existir
   em nenhum Pod, Secret de runtime, Job Flyway ou configuração da Lambda.

Em banco preexistente, revisar ownership, grants e memberships antes de adaptar o
bootstrap; não apagar objetos nem usar Flyway baseline automático para contornar
conflitos. O teste local usa um administrador PostgreSQL de Testcontainers; confirmar
as capacidades específicas do administrador gerenciado RDS no futuro aceite.

## Migrations e release

O Job chama `MigrationRunner` pela `PropertiesLauncher` na mesma imagem da API.
Esse entrypoint executa apenas Flyway: sem Spring, servidor HTTP, scheduler, Hibernate
ou seed ADMIN. Exige `MIGRATION_JDBC_URL`, `MIGRATION_USERNAME=oficina_migrator` e
`MIGRATION_PASSWORD`; impõe a mesma regra TLS. Clean desabilitado, sem baseline
automático e com validação de migrations. As migrations antigas não foram recriadas.

`k8s/staging/migration-job.yaml` fica **fora** do kustomization do runtime. Para cada
release: usar nome de Job único, definir o mesmo digest imutável da imagem no Job e
Deployment, criar o Job e aguardar `Complete`. Falha bloqueia o rollout; não executar
repair, clean ou retry cego. Há parallelism/completions=1, backoffLimit=0 e prazo de
600 segundos. Flyway ainda usa seu bloqueio no banco para concorrência acidental.
Serializar releases no processo de entrega; o runtime nunca migra ao escalar.

No bootstrap inicial, depois do primeiro Job e dos grants, iniciar uma réplica da API antes de habilitar o HPA. No staging já inicializado, o HPA controla as réplicas; o Deployment não fixa replicas.
Após seed ADMIN e readiness bem-sucedidos, pode-se escalar. Isso evita concorrência
na criação inicial da conta administrativa pelo bootstrap existente. Recolher
resultado/logs do Job sem segredos e remover a credencial de migration do namespace
quando fora da janela de release. Não montar esse Secret na API.

O overlay direto falha por ausência intencional de `image-digest.yaml`. Use
`bash scripts/staging/render.sh "$IMAGE"` para produzir YAML com digest obrigatório.
O script não consulta o cluster nem aplica recursos. Veja o
[runbook de consolidação](consolidation.md) para validação e rollback.
O Job permanece separado e exige substituição manual de seu placeholder pelo mesmo
digest revisado antes de qualquer execução de migration.

## Injeção de configuração e segredos

Não há objetos Secret com valores versionados em staging. Após criar o namespace,
o operador pode fornecer ConfigMaps de arquivos locais revisados:

```sh
kubectl -n oficina-staging create configmap oficina-staging-db --from-file=jdbc-url=/secure/jdbc-url
kubectl -n oficina-staging create configmap oficina-rds-ca --from-file=rds-ca.pem=/secure/rds-ca.pem
kubectl -n oficina-staging create configmap oficina-customer-jwt \
  --from-file=jwks-uri=/secure/jwks-uri --from-file=issuer=/secure/issuer \
  --from-file=audience=/secure/audience --from-file=customer-jwt.yml=/secure/customer-jwt.yml \
  --from-file=staging-demo-public.pem=/secure/staging-demo-public.pem
kubectl -n oficina-staging create secret generic oficina-runtime --from-env-file=/secure/runtime.env
kubectl -n oficina-staging create secret generic oficina-application --from-env-file=/secure/application.env
kubectl -n oficina-staging create secret generic oficina-migration --from-env-file=/secure/migration.env
```

Comandos são exemplos para execução futura, não foram executados. Arquivos `.env`
contêm as chaves da tabela acima, devem ter permissões restritas e ficar fora do
repositório. Gerar via ferramenta de segredos aprovada e removê-los com procedimento
seguro após uso; não imprimir seu conteúdo. Secret Kubernetes usa base64, não garante
criptografia em repouso. RBAC mínimo e criptografia dos Secrets K3s precisam ser
verificados na infraestrutura. Nenhum operador de Secrets Manager foi presumido.
Alterações de ConfigMap/Secret exigem rollout controlado (chaves JWT são carregadas
na inicialização); não presumir hot reload.

## Rede e probes

Não há PostgreSQL, PVC de banco, initContainer de espera, LoadBalancer, NodePort ou
Ingress em staging. Service é ClusterIP; a exposição HTTPS fica para revisão futura.
Os Pods não montam token de ServiceAccount; executam como não root, sem capabilities,
com filesystem read-only e /tmp temporário. Requests/limits permanecem definidos.

Liveness usa `/actuator/health/liveness`, somente estado do processo; readiness usa
`/actuator/health/readiness`, incluindo banco. Ambos foram explicitamente liberados
no SecurityConfig; demais regras de autorização foram preservadas. Não reiniciar a
API continuamente por uma indisponibilidade temporária do RDS usando DB na liveness.

## Demonstração sintética e interoperabilidade

Criar cliente sintético e OS pela API administrativa existente. Usar somente fixtures
de teste ou CPF gerado para teste, nunca dado pessoal obtido de pessoa real. Registrar
o UUID retornado. Como operador de banco autorizado, habilitar explicitamente esse
UUID conforme o [procedimento do Prompt 6](../security/sql-lambda-stage-6.md). A API
runtime e a Lambda não podem escrever em customer_access. Ao terminar, inativar a
identidade e aguardar expiração dos tokens; desabilitar RS256 se não houver mais demo.

`CustomerJwtIntegrationTest.realPythonSignerAuthenticatesAsCustomer` importa o
`RS256Signer` **real** do repositório irmão via `scripts/staging/python-interop-token.py`.
A chave privada de teste é efêmera, gerada em memória e passada por stdin; só a pública
vai para arquivo temporário. Não há CPF nesse teste; usa UUID sintético. O token não
é impresso nem gravado. Python roda com `-B` para não criar pycache no irmão. O teste
passa o token pelo filtro Spring real, verifica UUID/ROLE_CUSTOMER na autorização de
propriedade e confirma 403 em ADMIN e em recurso desconhecido.

Executar com Python que tenha PyJWT e cryptography disponíveis:

```sh
mvn -B -Dtest=CustomerJwtIntegrationTest \
  -Dlambda.repo=/caminho/tech-challenge-oficina-auth-lambda \
  -Dlambda.python=/caminho/python test
```

Se a fonte irmã não estiver disponível, apenas esse teste é marcado como ignorado
com motivo; os demais testes continuam. Dependências Python ausentes provocam falha.
No ambiente desta entrega, fonte e dependências estavam disponíveis e o teste rodou.
A suíte Python completa não foi executada nem alterada; seu harness continua com a
pendência V10/bootstrap indicada no Prompt 6. Não foi invocado handler/API Gateway
nem o lookup Python no RDS: esta prova cobre o emissor real e a validação Spring.

## Aceite obrigatório antes do futuro deployment AWS

- Revisar/provisionar as stacks independentes com autorização própria; corrigir
  db_name em infra-rds antes disso e validar os outputs reais.
- Confirmar conectividade privada e SG 5432 restrito, DNS e saída necessária;
  verificar RBAC, Secrets criptografados no K3s, distribuição e rotação de credenciais.
- Validar CA RDS, conexão verify-full ao hostname real e recusa de hostname/CA errados.
- Executar bootstrap com administrador RDS e provar privilégios mínimos com LOGINs
  reais. Não distribuir a credencial administrativa para aplicações.
- Executar Job com imagem por digest, V1–V11 aplicadas e grants; só depois liberar o
  runtime. Confirmar que replicas não executam Flyway e não possuem DDL.
- Verificar probes, limites, logs sem segredos, login HMAC e isolamento CUSTOMER com
  token Python sintético. Manter emissão CPF fora de acesso público.
- Definir HTTPS/Ingress e controles de exposição separadamente; definir SMTP antes
  de habilitar a outbox; validar backup/restore e plano de rollback de release/schema.

Não demonstrados nesta etapa: conexão RDS/AWS, comportamento de grants no RDS gerenciado,
Pods K3s, pull da imagem, distribuição de Secrets, failover ou restore. Renderização
local e Testcontainers não substituem esses aceites.

## Evidência histórica do commit recuperado (antes da reconciliação)

- `mvn -B clean verify`: BUILD SUCCESS, **150 testes**, zero falhas, erros ou ignorados;
  limites de cobertura JaCoCo aprovados. Inclui regressões dos Prompts 5–7.
- Quatro testes de configuração staging e um teste PostgreSQL/Flyway/roles/TLS
  aprovados; grants aplicados pelos próprios scripts, migrations pelo usuário sem
  SUPERUSER e conexões reais com runtime/Lambda.
- Emissor Python real executado; UUID e ROLE_CUSTOMER observados no controle de
  propriedade Spring; acesso ADMIN recusado. Python local usou PyJWT 2.7.0; isso não
  substitui a validação do bundle/dependências da Lambda no ambiente de destino.
- `kubectl kustomize k8s/staging` e `kubectl kustomize k8s/local`: renderização aprovada,
  sem consulta ao cluster. Job é validado separadamente pela revisão dos manifests.
- Entrypoint PropertiesLauncher no JAR empacotado: execução sem variáveis de migration
  terminou com falha de configuração antes de qualquer conexão.
- `git diff --check`: aprovado.
- Não executados: deployment/validação server-side Kubernetes, RDS/AWS, Terraform,
  suíte Python completa, handler/API Gateway, testes de backup/restore e SMTP.

O arquivo usado em `--from-file=jdbc-url` deve conter a URL sem quebra de linha final;
o guard rejeita URLs com caracteres inválidos, em vez de corrigir configuração
silenciosamente.
