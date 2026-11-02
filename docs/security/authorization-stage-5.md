# Autorização HTTP — etapa 5

A reconciliação mantém a consulta por POST/JSON da main. O GET legado existe
somente no perfil local e recebe a mesma verificação de ownership. A cadeia
`/api/v1/customer/**` da main permanece independente, com JWKS e ownership por UUID.
Veja o [relatório da reconciliação](../fase3/reconciliation.md).

## Achados históricos da etapa 5

- `SecurityConfig` exigia apenas `authenticated()` para qualquer rota não pública,
  incluindo todas as operações administrativas.
- `UserDetailsSecurityAdapter` usa `User.builder().roles(...)`: `ADMIN` persistido
  vira `ROLE_ADMIN`; `CUSTOMER` vira `ROLE_CUSTOMER`. Portanto, usamos `hasRole`.
- O filtro HMAC valida assinatura e expiração e carrega as authorities do usuário
  persistido via `UserDetailsService`; não promove privilégios a partir de claims
  `roles`, corpo ou query. Filtro, emissor e login não foram alterados.
- Consulta de status, aprovação e recusa externas conferiam apenas código e
  CPF/CNPJ informado. Esses dados não comprovam a identidade do proprietário.

## Inventário e regras

| Rotas existentes | Regra nesta etapa |
| --- | --- |
| `POST /api/v1/auth/login` | Pública; credenciais verificadas pelo fluxo existente |
| `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | Públicas, preservando observabilidade |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | Públicas |
| `/api/v1/admin/customers` e `/{id}` | ADMIN: criar, listar, detalhar, atualizar, remover |
| `/api/v1/admin/vehicles` e `/{id}` | ADMIN: criar, listar, detalhar, atualizar, remover |
| `/api/v1/admin/services` e `/{id}` | ADMIN: criar, listar, detalhar, atualizar, inativar |
| `/api/v1/admin/parts` e subrotas | ADMIN: CRUD, estoque mínimo, aumento e baixa de estoque |
| `/api/v1/admin/work-orders` e subrotas | ADMIN: criar, listar, detalhar, iniciar/atualizar diagnóstico, incluir itens, aprovar, notificar, finalizar, entregar |
| `GET /api/v1/admin/metrics/average-execution-time` | ADMIN |
| `POST /api/v1/admin/notifications/outbox/process` | ADMIN |
| `POST /api/v1/public/work-orders/{code}/status` | ADMIN ou CUSTOMER proprietário |
| `POST /api/v1/public/work-orders/{code}/budget/approve` | ADMIN ou CUSTOMER proprietário |
| `POST /api/v1/public/work-orders/{code}/budget/reject` | ADMIN ou CUSTOMER proprietário |
| Demais rotas | Negadas por padrão |

O namespace administrativo inteiro exige ADMIN, em todos os métodos HTTP.
Dispatches internos de erro são permitidos para preservar o status original;
isso não torna uma requisição direta a `/error` pública.

As três URLs com segmento `public` foram mantidas, assim como seus DTOs, mas agora
exigem autenticação. Acesso anônimo a elas retorna 401. Essa mudança de acesso é
necessária: manter o caminho anônimo permitiria contornar a validação de propriedade
simplesmente omitindo o token. Links de notificações também passam a exigir token.

`WorkOrderAuthorization`, aplicado por segurança de método ao controller, usa
exclusivamente o nome da identidade autenticada como UUID do cliente e compara com
`workOrder.customer.id` persistido, em transação. Código seleciona o recurso, nunca
identifica o usuário. Documento continua como validação adicional do caso de uso.
Não existem endpoints de autoatendimento de clientes/veículos; seus CRUDs seguem
exclusivos de ADMIN, inclusive para dados do próprio CUSTOMER.

Identidade CUSTOMER sem UUID canônico, OS inexistente ou sem proprietário conhecido
não recebe autorização (403). Identidade sem papel permitido também recebe 403;
ausência de autenticação ou token inválido em rota protegida recebe 401.
A propriedade da OS não possui operação de transferência no código atual. Caso
isso mude, a verificação e mutação precisarão compartilhar a transação/bloqueio.

## Testes e integração futura (contexto histórico da etapa 5)

`AuthorizationIntegrationTest` usa PostgreSQL/Testcontainers, filtro de segurança,
controllers e casos de uso reais. CUSTOMER é injetado como identidade autenticada
pelo suporte de testes do Spring, sem habilitar outro validador JWT. Cobre todas as
rotas administrativas mapeadas, propriedade nas três operações externas, tentativas
de falsificar identidade no corpo/query, UUID inválido, recurso inexistente,
rotas não previstas, login HMAC real, senha incorreta e token adulterado/inválido.
`WorkOrderFlowIntegrationTest` preserva os testes de negócio, idempotência e
concorrência, agora autenticando as chamadas externas com o login administrativo.

RS256 não foi habilitado nem validado ponta a ponta nesta etapa. A próxima etapa
precisa validar assinatura, algoritmo, emissor, audiência, validade e chaves,
produzir `ROLE_CUSTOMER` a partir de claims confiáveis e definir o principal como
o UUID do cliente persistido (não apenas um UUID de usuário sem vínculo).
Também deve testar convivência HMAC/RS256, rejeição de tokens cruzados e isolamento
entre clientes usando tokens reais. Até lá, tokens da Lambda não concedem acesso.
Não há novo cadastro de CUSTOMER, emissão de tokens de cliente ou mudança no domínio.

Nenhuma infraestrutura foi alterada; nenhum comando AWS/Terraform foi executado.

## Evidência histórica do commit recuperado (antes da reconciliação)

- `mvn -B clean verify`: BUILD SUCCESS; 97 testes, zero falhas, erros ou ignorados.
- Nova suíte de autorização: seis execuções aprovadas, incluindo três operações
  de cliente parametrizadas e verificação de todos os mappings administrativos.
- Verificação de cobertura JaCoCo aprovada; `git diff --check` sem problemas.
- Tentativa inicial com perfil H2 falhou na inicialização da consulta de
  idempotência existente (`Timestamp` versus `OffsetDateTime`). A suíte definitiva
  usa PostgreSQL/Testcontainers, como os demais testes de integração do projeto.
- Testes ponta a ponta de RS256 não executados: integração fora desta etapa.
