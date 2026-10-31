# Arquitetura proposta — não implantada

```mermaid
flowchart LR
  Client[Cliente] -->|POST /auth/token HTTPS| GW[API Gateway HTTP API]
  GW -->|proxy payload 2.0| Auth[Lambda CPF]
  Auth -->|SELECT view TLS| DB[(RDS PostgreSQL privado)]
  Auth -->|RS256| JWT[JWT CUSTOMER 15 min]
  Client -->|Bearer HTTPS| Ingress[Ingress TLS K3s / EC2]
  Ingress --> API[Spring Boot]
  API -->|JDBC TLS| DB
  API -->|chaves públicas| JWKS[JWKS HTTPS por ambiente]
  Keys[Secrets Manager chave privada] --> Auth
  API --> Metrics[Prometheus / Grafana privados]
```

## RDS e identidade

API recebe `SPRING_DATASOURCE_URL=jdbc:postgresql://<endpoint>:5432/oficina_db?sslmode=verify-full&sslrootcert=/etc/rds-ca/global-bundle.pem`,
`SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` por Secret Kubernetes
injetado no deploy futuro. CA RDS confiável montada como arquivo. Nunca commitar
Secret preenchido, senha em tfvars/state/outputs ou imprimir conteúdo nos logs.
Usar usuários separados para migrations, API e consulta auth. Migrador recebe DDL;
API recebe DML necessário; Lambda só SELECT na view `auth.customer_identity_v1`.
Migração futura deve separar credencial Flyway da credencial runtime (hoje compartilham datasource).

A aplicação é dona da view e grants via Flyway: customer_id (UUID), cpf normalizado
(11 caracteres), active (boolean). Adaptar colunas reais na migração, com índice único
adequado e tratamento de clientes CNPJ fora do login CPF. Lambda usa consulta
parametrizada, retorno único, timeout e conexões limitadas por reserved concurrency;
não copia regras ou entidades Java. Banco indisponível retorna 503, sem emitir JWT.

API Gateway HTTP API pertence ao repo auth: rota POST /auth/token, integração proxy
payload 2.0, permissão invoke restrita a API/stage/rota; body <=1 KiB, throttling,
logs sem CPF/token. Resposta 401 igual para CPF ausente/inativo; validar dígitos
verificadores antes da consulta. CPF sozinho não comprova posse: risco aceito apenas
para o exercício, exigindo fator adicional antes de produção real.

## Assinatura e validação JWT

Contrato futuro RS256 apenas; JWT header kid; claims iss por ambiente,
aud=oficina-api-<environment>, sub=UUID, role=CUSTOMER, iat/nbf/exp, TTL=900 s,
tolerância de relógio <=60 s. Não incluir CPF; nunca conceder ADMIN a login CPF.
Spring Security Resource Server deve verificar assinatura, algoritmo, issuer,
audience, validade e converter role explicitamente. Rejeitar alg none/HMAC e kid
inesperado. Configurar URI JWKS HTTPS por ambiente; cache limitado e falha fechada
quando nenhuma chave válida estiver disponível.

**Estado atual:** API usa `APP_JWT_SECRET` e validador HMAC; esta PR não altera o runtime.
Uma PR posterior deverá migrar emissão/validação, inclusive login administrativo,
antes de integrar a Lambda. Não ativar aceitação simultânea indiscriminada HS/RS.
No contrato futuro, token emitido antes da inativação pode durar até 15 min; revogação imediata exigiria
consulta/introspecção ou denylist e será decisão explícita.

Chave RSA privada futura por ambiente em Secrets Manager, leitura somente pela
role auth e cache em memória; distribuição pública JWKS via S3 HTTPS, sem chave
privada. Rotação: publicar chave pública nova, aguardar propagação/cache, trocar kid
do emissor, manter chave anterior por TTL + tolerância + cache máximo, depois retirar.
Sem KMS assimétrico inicialmente para reduzir dependências e chamadas de assinatura;
criptografia em repouso dos segredos continua obrigatória. Lambda precisará endpoint
VPC Secrets Manager ou alternativa aprovada; subnet privada não ganha internet pelo
API Gateway. Esse custo está incluído no planejamento, sem NAT Gateway.

## Isolamento, operação e riscos

staging/production: VPCs, subnets, instâncias, bancos, SGs, secrets, chaves, issuer,
roles OIDC, namespaces, registries/tags e prefixes state separados. Duas contas seria
o ideal; no Free Plan adotar isolamento dentro da conta, sujeito à elegibilidade.
Não compartilhar banco nem chave JWT. Para poupar créditos, provisionar inicialmente
somente staging após aprovação; production continua projeto até orçamento específico.

K3s single-node em EC2 t3.small proposto, EBS criptografado, IMDSv2 obrigatório,
sem SSH público e sem Kubernetes API 6443 pública. Administração futura via SSM;
runner GitHub não recebe kubeconfig administrativo. Ingress HTTPS público somente 443,
SSM por saída HTTPS da subnet pública; restringir egress e IAM. Fixar versão/checksum
K3s e AMI na implementação, sem curl|sh sem verificação. RDS privado em subnets de
duas AZs, instância inicialmente Single-AZ. Não usar EKS, NAT Gateway ou ALB inicial.
Single-node/Single-AZ não oferece HA e pode sofrer pressão de memória com observabilidade.

**Estado atual do Actuator:** `SecurityConfig` inclui `/actuator/prometheus` em
`permitAll()`, sem condicionar essa regra a um perfil local. O runtime local permite
acesso anônimo e `ActuatorSecurityIntegrationTest.shouldExposePrometheusMetricsWithoutJwt`
exige HTTP 200 e métricas sem JWT. Se esse mesmo runtime for exposto na cloud sem
restrições adicionais, a rota continuará anônima. Não confundir com `/actuator/metrics`,
que não aparece na lista pública. Esta correção não muda essa configuração.

**Gate de observabilidade cloud:** antes de expor a aplicação, restringir a rota de
scrape ao coletor autorizado, via configuração de segurança e rede; impedir acesso
público por Ingress, Service e porta direta do nó. Validar de fora da rede que
`/actuator/prometheus` não retorna métricas e, do coletor, que o scrape autorizado
funciona. Probes de saúde devem continuar operacionais. Registrar testes negativos
(sem identidade e de origem não autorizada) e positivos, além da configuração aplicada.
Somente esses resultados poderão demonstrar a restrição cloud — não o CI atual.

Prometheus/Grafana privados, scrape autenticado, retenção curta e volumes limitados
são requisitos futuros. Logs sem CPF/JWT/senha e alarmes de erros, latência, saturação,
backup e gasto também precisam implementação e evidência em cloud.

## Gate obrigatório de compatibilidade app ↔ auth

**Gate aberto (não satisfeito): não integrar nem habilitar o fluxo Lambda/API Gateway
até comprovar os itens abaixo. Aprovação deste bootstrap não fecha o gate.**

O adapter atual `JwtTokenSecurityAdapter` usa `Keys.hmacShaKeyFor`, `signWith` e
`verifyWith` com segredo compartilhado; sub é username, claim emitida é `roles`
(lista), e o filtro carrega `UserDetailsService` pelo username e usa suas authorities.
O default de expiração atual é 120 minutos (`application.yml`), configurável por
`APP_JWT_EXPIRATION_MINUTES`. Nenhuma dessas propriedades demonstra compatibilidade
com sub UUID, claim singular `role=CUSTOMER`, TTL 900 s e validação RS256/JWKS futura.

Responsáveis: aplicação (validação, principal e autorização) e auth (emissão e chaves).
A futura integração exige revisão conjunta e evidências versionadas:

1. Implementar e testar validação RS256 com JWKS confiável, kid, iss/aud por ambiente,
   exp/nbf/iat e tolerância <=60 s; rejeitar assinatura inválida, none, HMAC indevido,
   issuer/audience errados, token expirado e chave desconhecida. Testar falha fechada
   e rotação com cache de chaves. Não apenas configurar uma URI no runtime HMAC.
2. Mapear UUID de cliente e role CUSTOMER para o principal correto e provar que
   cliente não acessa endpoints ADMIN; preservar login e autorização administrativos
   por estratégia de migração explícita, sem confiar no algoritmo declarado pelo token.
3. Implementar view `auth.customer_identity_v1`, grants read-only e consulta
   parametrizada com TLS; testar cliente ativo, inativo/ausente, CPF inválido e falha
   de banco, sem token nos casos de falha. View/grants ainda não existem neste bootstrap.
4. Executar teste ponta a ponta local com token assinado pelo emissor compatível,
   validação na API e respostas HTTP de sucesso/recusa; registrar commits dos dois
   repositórios e versão do contrato. Depois, em tarefa AWS autorizada, repetir com
   Lambda/API Gateway reais antes de liberar tráfego. Guardar evidência por ambiente.

Os schemas de auth apenas validam formato de request/response; `access_token` é uma
string opaca. Os cinco testes de contrato não comprovam nenhum dos gates acima.
