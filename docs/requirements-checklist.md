# Checklist de atendimento ao Tech Challenge

| Requisito | Implementação |
|---|---|
| Back-end monolítico | Projeto único Spring Boot em camadas |
| DDD | Domínio com `Customer`, `Vehicle`, `ServiceCatalogItem`, `Part`, `WorkOrder` e regras no agregado |
| Criação da OS | `POST /api/v1/admin/work-orders` |
| Identificação por CPF/CNPJ | `WorkOrderCustomerInput.documentNumber` + Value Object `DocumentNumber` |
| Cadastro de veículo | Criado automaticamente na OS se a placa ainda não existir; CRUD próprio em `/vehicles` |
| Inclusão de serviços solicitados | Lista `services` na criação da OS e endpoint `/items` |
| Inclusão de peças e insumos | Lista `parts` na criação da OS e endpoint `/items` |
| Orçamento automático | Totais calculados em `WorkOrder.recalculateTotals()` |
| Envio do orçamento para aprovação | Representado por status `WAITING_APPROVAL` |
| Status da OS | Enum `WorkOrderStatus` |
| Alteração automática dos status | Métodos `addRequestedService`, `approveBudget`, `finish`, `deliver` |
| Consulta do cliente | `GET /api/v1/public/work-orders/{code}/status?document=...` |
| CRUD clientes | `/api/v1/admin/customers` |
| CRUD veículos | `/api/v1/admin/vehicles` |
| CRUD serviços | `/api/v1/admin/services` |
| CRUD peças e estoque | `/api/v1/admin/parts` + endpoints de estoque |
| Listagem/detalhamento OS | `/api/v1/admin/work-orders` e `/{id}` |
| Tempo médio de execução | `/api/v1/admin/metrics/average-execution-time` |
| JWT | `/api/v1/auth/login` + `JwtAuthenticationFilter` |
| Validação CPF/CNPJ | Value Object `DocumentNumber` + `DocumentValidator` |
| Validação placa | Value Object `VehiclePlate` + `VehiclePlateValidator` |
| Swagger | `springdoc-openapi` em `/swagger-ui.html` |
| Dockerfile | `Dockerfile` |
| docker-compose | `docker-compose.yml` com API + PostgreSQL |
| Testes unitários | `src/test/java/.../domain` |
| Testes de aplicação com mocks | `WorkOrderApplicationServiceTest` |
| Testes de status de erro | `ApiExceptionHandlerTest` |
| Teste de integração | `WorkOrderFlowIntegrationTest` |
| Cobertura 80% domínio crítico | `jacoco-maven-plugin` no `pom.xml` |
| README local | `README.md` |
