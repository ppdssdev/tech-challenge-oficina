# Oficina Tech Challenge API

MVP de back-end para uma oficina mecânica de médio porte, com foco em gestão de clientes, veículos, serviços, peças/insumos, estoque e ordens de serviço.

O projeto foi construído como **monólito Spring Boot em camadas**, usando **DDD pragmático** para concentrar as principais regras no domínio, principalmente no agregado `WorkOrder`.

---

## Stack

- Java 21
- Spring Boot 3.5.0
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- PostgreSQL
- Flyway
- Swagger/OpenAPI
- Docker e Docker Compose
- JUnit 5
- JaCoCo
- H2 para testes automatizados

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
- Aprovação do orçamento.
- Baixa de estoque das peças somente no momento da aprovação.
- Finalização da OS.
- Entrega da OS.
- Consulta pública de andamento por código da OS + CPF/CNPJ.

### Status da OS

- `RECEIVED` — Recebida
- `IN_DIAGNOSIS` — Em diagnóstico
- `WAITING_APPROVAL` — Aguardando aprovação
- `IN_EXECUTION` — Em execução
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
- Testes unitários de domínio.
- Testes de serviços de aplicação com mocks.
- Testes de tratamento de exceções e status HTTP.
- Teste de integração do fluxo principal da OS.
- JaCoCo configurado para cobertura mínima de 80% nos pacotes críticos de domínio.

---

## Estrutura de pacotes

```text
src/main/java/br/com/fiap/techchallenge/oficina
├── api
│   ├── controller
│   └── dto
├── application
│   └── service
├── config
├── domain
│   ├── exception
│   ├── model
│   └── service
├── infrastructure
│   └── repository
└── security
```

### Camadas

- `api`: entrada REST, DTOs e tratamento de erros.
- `application`: orquestração dos casos de uso.
- `domain`: entidades, estados, validações e regras de negócio.
- `infrastructure`: persistência com Spring Data JPA.
- `security`: JWT, autenticação e filtro de segurança.

### DDD no projeto

O projeto usa DDD de forma pragmática dentro de um monólito em camadas:

- `WorkOrder` é o agregado central da ordem de serviço e protege o ciclo de vida da OS.
- `WorkOrderServiceItem` e `WorkOrderPartItem` pertencem ao agregado `WorkOrder`.
- `DocumentNumber` é um Value Object para CPF/CNPJ, mantendo normalização e validação no domínio.
- `VehiclePlate` é um Value Object para placa, mantendo normalização e validação no domínio.
- `Part` controla regras de estoque, como baixa, incremento, estoque mínimo e concorrência.
- Serviços de aplicação coordenam casos de uso e transações, mas regras críticas ficam no domínio.

---

## Como executar localmente com Docker

### Pré-requisitos

- Docker
- Docker Compose

### Subir ambiente completo

Na raiz do projeto:

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

Credenciais do banco:

```text
database: oficina_db
username: oficina
password: oficina
```

---

## Como executar localmente sem Docker para a aplicação

Suba apenas o PostgreSQL:

```bash
docker compose up -d postgres
```

Depois execute a aplicação pela IDE ou via Maven:

```bash
mvn spring-boot:run
```

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

---

## Swagger

Com a aplicação rodando, acesse:

```text
http://localhost:8080/swagger-ui.html
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

Executar todos os testes automatizados:

```bash
mvn test
```

Executar testes, empacotamento e verificação de cobertura JaCoCo:

```bash
mvn clean verify
```

Relatório JaCoCo:

```text
target/site/jacoco/index.html
```

Tipos de teste existentes:

- domínio: regras puras de `WorkOrder`, `Part`, `Customer`, Value Objects e validators;
- aplicação: serviços de aplicação com mocks para cenários de erro;
- API: fluxo REST com Spring Boot, autenticação JWT e consulta pública;
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

```http
POST /api/v1/admin/work-orders
GET  /api/v1/admin/work-orders
GET  /api/v1/admin/work-orders?status=WAITING_APPROVAL
GET  /api/v1/admin/work-orders/{id}
POST /api/v1/admin/work-orders/{id}/diagnosis/start
POST /api/v1/admin/work-orders/{id}/diagnosis/notes
POST /api/v1/admin/work-orders/{id}/items
POST /api/v1/admin/work-orders/{id}/approve
POST /api/v1/admin/work-orders/{id}/finish
POST /api/v1/admin/work-orders/{id}/deliver
```

### Consulta pública do cliente

```http
GET /api/v1/public/work-orders/{code}/status?document=52998224725
```

### Métricas

```http
GET /api/v1/admin/metrics/average-execution-time
```

Com filtro opcional:

```http
GET /api/v1/admin/metrics/average-execution-time?from=2026-06-01T00:00:00Z&to=2026-06-30T23:59:59Z
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

### 3. Aprovar orçamento

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders/{id}/approve \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Finalizar OS

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders/{id}/finish \
  -H "Authorization: Bearer $TOKEN"
```

### 5. Entregar OS

```bash
curl -X POST http://localhost:8080/api/v1/admin/work-orders/{id}/deliver \
  -H "Authorization: Bearer $TOKEN"
```

### 6. Consulta pública do cliente

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

- Não há envio real de notificação para o cliente. O orçamento fica disponível na resposta da API e a OS passa para `WAITING_APPROVAL`.
- Não há módulo financeiro completo. O foco está em orçamento, autorização, execução e controle de estoque.
- A baixa de estoque acontece na aprovação do orçamento, não na criação da OS.
- O endpoint público exige documento do cliente para evitar consulta aberta apenas pelo código da OS.
- A aprovação da OS trava pessimisticamente as peças envolvidas antes de baixar estoque, reduzindo risco de corrida em aprovações concorrentes.
- A entidade `Part` usa `@Version` para detecção otimista de conflitos de atualização.
