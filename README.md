# Oficina Tech Challenge API

MVP de back-end para uma oficina mecânica de médio porte, com foco em gestão de clientes, veículos, serviços, peças/insumos, estoque e ordens de serviço.

O projeto foi construído como **monólito Spring Boot com arquitetura hexagonal**, usando **DDD pragmático** para concentrar as principais regras no domínio, principalmente no agregado `WorkOrder`.

---

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Web
- Spring Data JPA
- Spring Mail
- Spring Security
- JWT
- PostgreSQL
- Flyway
- Swagger/OpenAPI
- Docker e Docker Compose
- Mailpit para SMTP e caixa de e-mail locais
- JUnit 5
- JaCoCo
- H2 para testes rápidos de unidade/aplicação
- Testcontainers com PostgreSQL 16 para testes de integração

---

## Por que PostgreSQL?

O PostgreSQL foi escolhido por ser um banco relacional robusto, transacional e adequado para um sistema administrativo com dados consistentes entre clientes, veículos, ordens de serviço, serviços e peças.

A escolha é coerente com o domínio porque o sistema precisa de:

- integridade referencial entre cliente, veículo e OS;
- transações para aprovação de orçamento e baixa de estoque;
- tipos numéricos precisos para valores monetários;
- suporte nativo a UUID;
- boa maturidade para consultas administrativas e métricas.

---

## Funcionalidades implementadas

### Ordem de Serviço

- Criação de OS com identificação do cliente por CPF/CNPJ.
- Cadastro automático do cliente, caso ainda não exista.
- Cadastro automático do veículo, caso ainda não exista.
- Inclusão de serviços solicitados.
- Inclusão de peças e insumos necessários.
- Orçamento calculado automaticamente.
- Status automático para `WAITING_APPROVAL` quando existe orçamento.
- Notificação de orçamento persistida em outbox e enviada ao Mailpit, sem provedor externo pago.
- Aprovação ou recusa externa do orçamento pelo cliente.
- Baixa de estoque das peças somente no momento da aprovação.
- Finalização da OS.
- Entrega da OS.
- Consulta pública de andamento por código da OS + CPF/CNPJ.

### Status da OS

- `RECEIVED` — Recebida
- `IN_DIAGNOSIS` — Em diagnóstico
- `WAITING_APPROVAL` — Aguardando aprovação
- `IN_EXECUTION` — Em execução
- `BUDGET_REJECTED` — Orçamento recusado
- `FINALIZED` — Finalizada
- `DELIVERED` — Entregue

### Gestão administrativa

- CRUD de clientes.
- CRUD de veículos.
- CRUD de serviços.
- CRUD de peças/insumos.
- Controle de estoque.
- Listagem e detalhamento de ordens de serviço.
- Filtro de OS por status.
- Métrica de tempo médio de execução.

### Segurança e qualidade

- Login administrativo com JWT.
- APIs administrativas protegidas.
- Consulta pública da OS sem JWT, mas exigindo CPF/CNPJ do cliente.
- Validação de CPF/CNPJ com Value Object `DocumentNumber`.
- Validação de placa nos formatos brasileiro antigo e Mercosul com Value Object `VehiclePlate`.
- Controle de concorrência no estoque com `@Version` em `Part` e locks pessimistas nos fluxos de baixa/incremento.
- Imagem Docker final baseada em Alpine, com pacotes atualizados e execução por usuário não-root.
- Dependências revisadas após scan de vulnerabilidades com Trivy.
- Testes unitários de domínio.
- Testes de serviços de aplicação com mocks.
- Testes de tratamento de exceções e status HTTP.
- Teste de integração do fluxo principal da OS.
- JaCoCo configurado para cobertura mínima de 80% nos pacotes críticos de domínio.

---

## Estrutura de pacotes

```text
src/main/java/br/com/fiap/techchallenge/oficina
├── adapters
│   ├── in/web
│   └── out/{notification,persistence,security}
├── application
│   ├── port/{in,out}
│   └── usecase
├── configuration
└── domain
    ├── exception
    ├── model
    └── service
```

### Camadas

- `adapters/in`: entrada REST, DTOs e tratamento de erros.
- `application`: portas e orquestração dos casos de uso.
- `domain`: entidades, estados, validações e regras de negócio.
- `adapters/out`: persistência com JPA, envio SMTP local e segurança.
- `configuration`: composição dos casos de uso e configuração Spring.

### DDD no projeto

O projeto usa DDD de forma pragmática dentro de um monólito em camadas:

- `WorkOrder` é o agregado central da ordem de serviço e protege o ciclo de vida da OS.
- `WorkOrderServiceItem` e `WorkOrderPartItem` pertencem ao agregado `WorkOrder`.
- `DocumentNumber` é um Value Object para CPF/CNPJ, mantendo normalização e validação no domínio.
- `VehiclePlate` é um Value Object para placa, mantendo normalização e validação no domínio.
- `Part` controla regras de estoque, como baixa, incremento, estoque mínimo e concorrência.
- Serviços de aplicação coordenam casos de uso e transações, mas regras críticas ficam no domínio.

### Documentação técnica

- [Arquitetura e DDD](docs/architecture.md)
- [Checklist de requisitos](docs/requirements-checklist.md)
- [Exemplos de chamadas HTTP](docs/api-examples.http)

Os artefatos finais da fase, como PDF de entrega, board de Event Storming/Miro, roteiro do vídeo e relatório formal de vulnerabilidades, são preparados fora do repositório a partir destes documentos técnicos e dos resultados dos scans.

---

## Guia rápido para avaliadores

Este roteiro sobe o ambiente completo, autentica no Swagger e valida o fluxo principal da ordem de serviço.

### 1. Subir a aplicação

Na raiz do projeto:

```bash
docker compose up --build
```

Para rodar em segundo plano:

```bash
docker compose up -d
docker compose logs -f api
```

A caixa de e-mail local do Mailpit fica em `http://localhost:8025`; o SMTP local escuta em `localhost:1025`. Todo o fluxo usa ferramentas gratuitas e locais, sem SendGrid, Twilio, SES ou outro serviço externo pago.

Se já existir um banco local com migrations antigas e o Flyway acusar erro de checksum, zere o ambiente local de teste:

```bash
docker compose down -v
docker compose up --build
```

Use `docker compose down -v` somente quando puder apagar os dados locais do PostgreSQL.

### 2. Validar saúde da API

```bash
curl http://localhost:8080/actuator/health
```

Resposta esperada:

```json
{"status":"UP"}
```

### 3. Acessar Swagger

```text
http://localhost:8080/swagger-ui/index.html
```

Também pode funcionar via:

```text
http://localhost:8080/swagger-ui.html
```

### 4. Fazer login administrativo

Endpoint:

```http
POST /api/v1/auth/login
```

Payload:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Copie o campo `accessToken`, clique em `Authorize` no Swagger e informe:

```text
Bearer SEU_TOKEN_AQUI
```

### 5. Executar fluxo principal da OS

Crie uma ordem de serviço:

```http
POST /api/v1/admin/work-orders
```

Payload:

```json
{
  "customer": {
    "fullName": "Maria Silva",
    "documentType": "CPF",
    "documentNumber": "529.982.247-25",
    "email": "maria@email.com",
    "phone": "31999999999"
  },
  "vehicle": {
    "plate": "ABC1D23",
    "brand": "Fiat",
    "model": "Argo",
    "manufacturingYear": 2020
  },
  "services": [
    {
      "serviceId": "11111111-1111-1111-1111-111111111111",
      "quantity": 1
    }
  ],
  "parts": [
    {
      "partId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      "quantity": 1
    }
  ],
  "diagnosticNotes": "Cliente solicitou manutenção preventiva."
}
```

Copie da resposta:

- `id`: usado nos próximos endpoints administrativos;
- `code`: usado na consulta pública do cliente;
- `status`: esperado como `WAITING_APPROVAL`, pois a OS já nasce com orçamento calculado.

Depois execute, nesta ordem:

```http
POST /api/v1/admin/work-orders/{id}/budget/notify
POST /api/v1/admin/notifications/outbox/process
POST /api/v1/public/work-orders/{code}/budget/approve
POST /api/v1/admin/work-orders/{id}/finish
POST /api/v1/admin/work-orders/{id}/deliver
```

O endpoint de notificação não altera o status da OS. Ele grava uma mensagem `PENDING` na outbox e retorna a prévia com os links públicos. O scheduler processa a fila automaticamente; o endpoint administrativo permite dispará-la manualmente na demonstração. Após o envio, o e-mail aparece no Mailpit e a mensagem passa a `SENT`; uma falha isolada vira `FAILED`, registra `attempts` e `lastError` e não interrompe o restante do lote.

O endpoint `POST /api/v1/admin/work-orders/{id}/diagnosis/start` é usado para OS em `RECEIVED`. No fluxo acima, a OS já vai para `WAITING_APPROVAL`; se chamar diagnóstico nesse estado, a API retorna `422 Unprocessable Entity`, porque viola a transição permitida do domínio.

### 6. Consultar andamento pelo cliente

```http
GET /api/v1/public/work-orders/{code}/status?document=52998224725
```

Esse endpoint é público, mas exige o documento do cliente para evitar consulta aberta somente pelo código da OS.

O cliente também pode decidir o orçamento sem JWT, informando seu documento no corpo:

```http
POST /api/v1/public/work-orders/{code}/budget/approve
POST /api/v1/public/work-orders/{code}/budget/reject
```

```json
{
  "document": "52998224725"
}
```

A aprovação move a OS para `IN_EXECUTION` e reserva o estoque pendente. A recusa do orçamento inicial move a OS para `BUDGET_REJECTED`, sem iniciar a execução ou baixar estoque.

### 7. Consultar tempo médio de execução

Sem filtro:

```http
GET /api/v1/admin/metrics/average-execution-time
```

Com intervalo:

```http
GET /api/v1/admin/metrics/average-execution-time?from=2026-06-30T00:00:00-03:00&to=2026-06-30T23:59:59-03:00
```

Os filtros `from` e `to` são opcionais e usam data/hora ISO com offset.

### 8. Testar CRUDs administrativos

Com o token JWT, os avaliadores também podem validar pelo Swagger:

- clientes: `/api/v1/admin/customers`;
- veículos: `/api/v1/admin/vehicles`;
- serviços: `/api/v1/admin/services`;
- peças e estoque: `/api/v1/admin/parts`;
- ordens de serviço: `/api/v1/admin/work-orders`.

---

## Execução local

### Pré-requisitos

- Docker
- Docker Compose

### Docker Compose

Subir ambiente completo:

```bash
docker compose up --build
```

A aplicação ficará disponível em:

```text
http://localhost:8080
```

O PostgreSQL ficará disponível em:

```text
localhost:5432
```

O Mailpit ficará disponível em:

```text
UI:   http://localhost:8025
SMTP: localhost:1025
```

Credenciais do banco:

```text
database: oficina_db
username: oficina
password: oficina
```

Para conectar no DBeaver ou outro client SQL:

```text
host: localhost
port: 5432
database: oficina_db
username: oficina
password: oficina
```

### Aplicação fora do Docker

Suba PostgreSQL e Mailpit:

```bash
docker compose up -d postgres mailpit
```

Depois execute a aplicação pela IDE ou via Maven:

```bash
mvn spring-boot:run
```

---

## Execução local com Kubernetes/Kind

Esta opção executa gratuitamente a API, o PostgreSQL e o Mailpit dentro de um cluster Kubernetes local criado com Kind. Ela não usa EKS ou outro serviço de cloud, registry privado, Helm, Ingress ou `LoadBalancer`; os acessos locais são feitos com `port-forward`.

### Pré-requisitos

- Docker com o daemon em execução
- `kubectl`
- `kind`

### 1. Criar o cluster

O nome `oficina-local` é informado pela linha de comando; o arquivo de configuração mantém apenas a topologia de um nó para continuar compatível com o schema do Kind.

```bash
kind create cluster \
  --name oficina-local \
  --config k8s/kind/oficina-kind.yaml
```

O script equivalente é idempotente e não recria o cluster caso ele já exista:

```bash
./scripts/kind-create.sh
```

### 2. Buildar e carregar a imagem local

Não é necessário publicar a imagem em um registry externo:

```bash
docker build -t tech-challenge-oficina:local .
kind load docker-image tech-challenge-oficina:local --name oficina-local
```

Ou execute os dois comandos com:

```bash
./scripts/kind-load-image.sh
```

Sempre repita o build e o carregamento após alterar a aplicação. O Deployment usa `imagePullPolicy: IfNotPresent` para aproveitar a imagem carregada diretamente nos nós do Kind.

### 3. Implantar os recursos

```bash
kubectl apply -k k8s/local
kubectl -n oficina rollout status deployment/oficina-api --timeout=300s
kubectl get pods -n oficina
kubectl get svc -n oficina
```

O script de deploy também aguarda PostgreSQL e Mailpit antes de aguardar a API:

```bash
./scripts/k8s-deploy-local.sh
```

Todos os recursos ficam no namespace `oficina`. O PostgreSQL usa um PVC local e seu Service é somente `ClusterIP`, portanto o banco não é exposto fora do cluster.

Para diagnóstico operacional:

```bash
kubectl -n oficina get pods,svc,pvc
kubectl -n oficina describe pod -l app=oficina-api
kubectl -n oficina logs deployment/oficina-api
```

### 4. Acessar e validar a API

Em um terminal, mantenha o port-forward ativo:

```bash
kubectl -n oficina port-forward svc/oficina-api 8080:8080
```

Em outro terminal, valide o Actuator:

```bash
curl http://localhost:8080/actuator/health
```

Resposta esperada:

```json
{"status":"UP"}
```

Com o port-forward ativo, a API e o Swagger ficam disponíveis em:

```text
API:     http://localhost:8080
Swagger: http://localhost:8080/swagger-ui/index.html
```

### 5. Acessar o Mailpit

Em outro terminal, mantenha o port-forward da interface web ativo:

```bash
kubectl -n oficina port-forward svc/oficina-mailpit 8025:8025
```

Acesse `http://localhost:8025`. Dentro do cluster, a API envia os e-mails por `oficina-mailpit:1025`. Como `APP_PUBLIC_BASE_URL` vale `http://localhost:8080`, os links de aprovação ou recusa enviados por e-mail funcionam enquanto o port-forward da API estiver ativo.

Para iniciar os dois port-forwards com um único comando e encerrá-los juntos com `Ctrl+C`:

```bash
./scripts/k8s-port-forward.sh
```

### 6. Destruir o ambiente

```bash
kubectl delete -k k8s/local
kind delete cluster --name oficina-local
```

Ou:

```bash
./scripts/k8s-destroy-local.sh
```

A exclusão do cluster remove também o volume local e os dados do PostgreSQL desse ambiente.

Os valores de `k8s/local/secret.yaml` são credenciais previsíveis criadas exclusivamente para demonstração local. Eles não são secrets reais de produção. Em um ambiente real, os valores sensíveis devem ser gerenciados por uma solução apropriada, com acesso restrito e sem versionamento no repositório. CI/CD será tratado posteriormente.

---

## Infraestrutura local com Terraform

Além dos comandos diretos com Kind e `kubectl`, o projeto possui um fluxo Terraform opcional que cria o cluster, builda e carrega a imagem local e aplica a mesma base Kustomize. Ele mantém estado apenas local, não usa Terraform Cloud, AWS/EKS, Helm, registry externo ou ferramenta paga.

Use apenas um fluxo para gerenciar o cluster `oficina-local`. Se ele foi criado pelos scripts do Kind, destrua-o antes do primeiro `terraform apply`.

```bash
cd infra/terraform/local
terraform init
terraform plan
terraform apply
export KUBECONFIG="$(terraform output -raw kubeconfig_path)"
```

Depois, os acessos continuam via port-forward:

```bash
kubectl -n oficina port-forward svc/oficina-api 8080:8080
curl http://localhost:8080/actuator/health
```

Para remover os manifests e o cluster gerenciados pelo Terraform:

```bash
terraform destroy
```

O guia detalhado está em [Terraform local com Kind](infra/terraform/local/README.md). Quem preferir pode continuar usando os scripts diretos documentados nesta seção.

---

## Login administrativo

Ao iniciar a aplicação, um usuário administrativo padrão é criado automaticamente se ainda não existir.

```text
username: admin
password: admin123
```

Esses valores podem ser alterados por variáveis de ambiente:

```text
APP_ADMIN_DEFAULT_USERNAME
APP_ADMIN_DEFAULT_PASSWORD
```

A URL base usada nos links da notificação é `http://localhost:8080` por padrão e pode ser alterada por:

```text
APP_PUBLIC_BASE_URL
```

O processamento da outbox e o remetente local podem ser configurados por `APP_NOTIFICATION_OUTBOX_ENABLED`, `APP_NOTIFICATION_OUTBOX_POLL_DELAY_MS`, `APP_NOTIFICATION_OUTBOX_BATCH_SIZE` e `APP_NOTIFICATION_EMAIL_FROM`.

---

## Swagger

Com a aplicação rodando, acesse:

```text
http://localhost:8080/swagger-ui/index.html
```

Fluxo recomendado no Swagger:

1. Fazer login em `POST /api/v1/auth/login`.
2. Copiar o `accessToken`.
3. Clicar em `Authorize`.
4. Informar o token no formato:

```text
Bearer SEU_TOKEN_AQUI
```

---

## Executar testes

Os testes de domínio e aplicação continuam rápidos e isolados; quando precisam de banco em memória, usam o profile `test` com H2. Os testes REST e de persistência usam o profile `it`, sobem um PostgreSQL 16 real via Testcontainers, executam todas as migrations Flyway e validam os mapeamentos JPA com `ddl-auto=validate`.

É necessário ter o Docker em execução para rodar a suíte completa. Se o daemon não estiver disponível, os testes que usam Testcontainers falharão.

Executar todos os testes automatizados:

```bash
mvn test
```

Executar testes, empacotamento e verificação de cobertura JaCoCo:

```bash
mvn clean verify
```

O Mailpit não é necessário para nenhum desses comandos. O envio de e-mail é validado com `JavaMailSender` mockado, e o scheduler da outbox fica desabilitado no profile `it`; o Mailpit é usado apenas na demonstração local com Docker Compose.

Relatório JaCoCo:

```text
target/site/jacoco/index.html
```

Executar scan de vulnerabilidades com Trivy via Docker:

```bash
mkdir -p target/security

docker run --rm -v "$PWD:/workspace" -v "$HOME/.cache/trivy:/root/.cache" \
  aquasec/trivy:latest fs --offline-scan --format table \
  --output /workspace/target/security/trivy-fs.txt /workspace

docker build --progress=plain -t oficina-api:local .

docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$HOME/.cache/trivy:/root/.cache" \
  -v "$PWD/target/security:/reports" \
  aquasec/trivy:latest image --offline-scan --format table \
  --output /reports/trivy-image.txt oficina-api:local
```

Tipos de teste existentes:

- domínio: regras puras de `WorkOrder`, `Part`, `Customer`, Value Objects e validators;
- aplicação: serviços de aplicação com mocks para cenários de erro;
- API/integração: fluxo REST, autenticação JWT, consulta pública, PostgreSQL real, Flyway e JPA validate;
- persistência: migrations, constraints e adapter da outbox contra PostgreSQL Testcontainers;
- exceções: validação dos status HTTP esperados, como `401`, `403`, `404`, `409` e `422`.

Status HTTP principais:

- `400 Bad Request`: payload, parâmetros ou tipos inválidos;
- `401 Unauthorized`: credenciais inválidas ou JWT ausente/inválido;
- `403 Forbidden`: usuário autenticado sem permissão;
- `404 Not Found`: recurso inexistente ou consulta pública com documento divergente;
- `409 Conflict`: conflito de unicidade/integridade;
- `422 Unprocessable Entity`: violação de regra de negócio.

---

## Endpoints principais

### Autenticação

```http
POST /api/v1/auth/login
```

### Clientes

```http
POST   /api/v1/admin/customers
GET    /api/v1/admin/customers
GET    /api/v1/admin/customers/{id}
PUT    /api/v1/admin/customers/{id}
DELETE /api/v1/admin/customers/{id}
```

### Veículos

```http
POST   /api/v1/admin/vehicles
GET    /api/v1/admin/vehicles
GET    /api/v1/admin/vehicles/{id}
PUT    /api/v1/admin/vehicles/{id}
DELETE /api/v1/admin/vehicles/{id}
```

### Serviços

```http
POST   /api/v1/admin/services
GET    /api/v1/admin/services
GET    /api/v1/admin/services/{id}
PUT    /api/v1/admin/services/{id}
DELETE /api/v1/admin/services/{id}
```

### Peças e insumos

```http
POST   /api/v1/admin/parts
GET    /api/v1/admin/parts
GET    /api/v1/admin/parts/below-minimum-stock
GET    /api/v1/admin/parts/{id}
PUT    /api/v1/admin/parts/{id}
POST   /api/v1/admin/parts/{id}/stock/increase
POST   /api/v1/admin/parts/{id}/stock/decrease
DELETE /api/v1/admin/parts/{id}
```

### Ordens de Serviço

`GET /api/v1/admin/work-orders` sem o parâmetro `status` retorna a fila operacional da oficina. A fila contém somente OS nos status `RECEIVED`, `IN_DIAGNOSIS`, `WAITING_APPROVAL` e `IN_EXECUTION`, priorizadas nessa ordem de atendimento: `WAITING_APPROVAL`, `IN_EXECUTION`, `IN_DIAGNOSIS` e `RECEIVED`. Dentro do mesmo status, as OS mais antigas aparecem primeiro.

OS nos status `BUDGET_REJECTED`, `FINALIZED` e `DELIVERED` não são exibidas na fila operacional padrão, mas continuam disponíveis por meio do filtro explícito, por exemplo `?status=BUDGET_REJECTED`, `?status=FINALIZED` ou `?status=DELIVERED`.

```http
POST /api/v1/admin/work-orders
GET  /api/v1/admin/work-orders
GET  /api/v1/admin/work-orders?status=WAITING_APPROVAL
GET  /api/v1/admin/work-orders/{id}
POST /api/v1/admin/work-orders/{id}/diagnosis/start
POST /api/v1/admin/work-orders/{id}/diagnosis/notes
POST /api/v1/admin/work-orders/{id}/items
POST /api/v1/admin/work-orders/{id}/budget/notify
POST /api/v1/admin/notifications/outbox/process
POST /api/v1/admin/work-orders/{id}/approve
POST /api/v1/admin/work-orders/{id}/finish
POST /api/v1/admin/work-orders/{id}/deliver
```

### Consulta pública do cliente

Os endpoints abaixo não exigem JWT. As decisões de orçamento exigem o documento do cliente no corpo da requisição para validar o acesso à OS.

```http
GET /api/v1/public/work-orders/{code}/status?document=52998224725
POST /api/v1/public/work-orders/{code}/budget/approve
POST /api/v1/public/work-orders/{code}/budget/reject
```

A aprovação move a OS para `IN_EXECUTION`; a recusa move o orçamento inicial para `BUDGET_REJECTED`. Os status `FINALIZED` e `DELIVERED` continuam reservados, respectivamente, à finalização técnica e à entrega do veículo.

### Notificação local de orçamento com outbox e Mailpit

O projeto não utiliza serviço pago nem provedor externo. Enquanto a OS estiver em `WAITING_APPROVAL`, o endpoint administrativo abaixo grava, na mesma transação da aplicação, a intenção de envio como `PENDING` na tabela `notification_outbox`:

```http
POST /api/v1/admin/work-orders/{id}/budget/notify
```

A resposta contém destinatário, assunto, corpo e os links `approveUrl` e `rejectUrl`. O cliente ainda precisa enviar seu CPF ou CNPJ no body ao chamar um desses endpoints `POST`. A notificação não altera o status da OS nem envia SMTP dentro da requisição. A outbox evita perder a intenção de notificação quando o SMTP está temporariamente indisponível.

Um scheduler consulta mensagens pendentes a cada cinco segundos. Para processar imediatamente, use o endpoint protegido por JWT:

```http
POST /api/v1/admin/notifications/outbox/process
```

Depois, abra `http://localhost:8025`, selecione o e-mail e use os links públicos de aprovação ou recusa. O SMTP do Mailpit é totalmente local e não é necessário para `mvn clean verify`.

Exemplo de resposta:

```json
{
  "workOrderCode": "OS-20260829-ABC123",
  "channel": "MAILPIT_EMAIL",
  "recipient": "cliente@email.com",
  "subject": "Orçamento da OS OS-20260829-ABC123 aguardando aprovação",
  "body": "Olá, Maria Silva. O orçamento está aguardando decisão...",
  "approveUrl": "http://localhost:8080/api/v1/public/work-orders/OS-20260829-ABC123/budget/approve",
  "rejectUrl": "http://localhost:8080/api/v1/public/work-orders/OS-20260829-ABC123/budget/reject"
}
```

### Métricas

```http
GET /api/v1/admin/metrics/average-execution-time
```

Os parâmetros `from` e `to` são opcionais e filtram pela data de finalização da OS (`finishedAt`).

Sem filtro:

```http
GET /api/v1/admin/metrics/average-execution-time
```

Somente data inicial:

```http
GET /api/v1/admin/metrics/average-execution-time?from=2026-06-30T00:00:00-03:00
```

Somente data final:

```http
GET /api/v1/admin/metrics/average-execution-time?to=2026-06-30T23:59:59-03:00
```

Intervalo completo:

```http
GET /api/v1/admin/metrics/average-execution-time?from=2026-06-30T00:00:00-03:00&to=2026-06-30T23:59:59-03:00
```

---

## Exemplo de fluxo com cURL

### 1. Login

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
```

### 2. Criar OS usando dados semeados pelo Flyway

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "customer": {
      "fullName": "Maria Silva",
      "documentType": "CPF",
      "documentNumber": "529.982.247-25",
      "email": "maria@email.com",
      "phone": "31999999999"
    },
    "vehicle": {
      "plate": "ABC1D23",
      "brand": "Fiat",
      "model": "Argo",
      "manufacturingYear": 2020
    },
    "services": [
      {
        "serviceId": "11111111-1111-1111-1111-111111111111",
        "quantity": 1
      }
    ],
    "parts": [
      {
        "partId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        "quantity": 1
      }
    ],
    "diagnosticNotes": "Cliente solicitou manutenção preventiva."
  }'
```

A resposta retorna o `id` e o `code` da OS.

### 3. Enfileirar a notificação do orçamento

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders/{id}/budget/notify \
  -H "Authorization: Bearer $TOKEN"
```

O retorno contém a prévia do e-mail e os links públicos de aprovação e recusa. A mensagem fica `PENDING` na outbox.

### 4. Processar a outbox imediatamente

```bash
curl -X POST http://localhost:8080/api/v1/admin/notifications/outbox/process \
  -H "Authorization: Bearer $TOKEN"
```

Resposta esperada para uma mensagem pendente: `{"processed":1,"sent":1,"failed":0}`. O e-mail pode ser visto em `http://localhost:8025`.

### 5. Aprovar ou recusar pelo link público

```bash
curl -X POST http://localhost:8080/api/v1/public/work-orders/{code}/budget/approve \
  -H "Content-Type: application/json" \
  -d '{"document":"52998224725"}'

curl -X POST http://localhost:8080/api/v1/public/work-orders/{code}/budget/reject \
  -H "Content-Type: application/json" \
  -d '{"document":"52998224725"}'
```

### 6. Finalizar OS

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders/{id}/finish \
  -H "Authorization: Bearer $TOKEN"
```

### 7. Entregar OS

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders/{id}/deliver \
  -H "Authorization: Bearer $TOKEN"
```

### 8. Consulta pública do cliente

```bash
curl "http://localhost:8080/api/v1/public/work-orders/{code}/status?document=52998224725"
```

---

## Dados iniciais

O Flyway cria alguns serviços e peças iniciais.

Serviços:

- Troca de óleo
- Alinhamento
- Balanceamento
- Diagnóstico eletrônico

Peças:

- Óleo sintético 5W30 1L
- Filtro de óleo
- Filtro de ar
- Pastilha de freio dianteira

---

## Observações sobre o MVP

- O envio de e-mail é local: a outbox persiste a intenção e o adapter SMTP entrega ao Mailpit. Não há dependência de serviço externo pago.
- O processamento é deliberadamente simples: sem retry avançado, DLQ, lock distribuído, Kafka ou RabbitMQ; falhas são marcadas como `FAILED`.
- Não há módulo financeiro completo. O foco está em orçamento, autorização, execução e controle de estoque.
- A baixa de estoque acontece na aprovação do orçamento, não na criação da OS.
- O endpoint público exige documento do cliente para evitar consulta aberta apenas pelo código da OS.
- A aprovação da OS trava pessimisticamente as peças envolvidas antes de baixar estoque, reduzindo risco de corrida em aprovações concorrentes.
- A entidade `Part` usa `@Version` para detecção otimista de conflitos de atualização.
