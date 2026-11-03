# Sequências verificadas no código

Os diagramas descrevem implementação e casos de uso, não um teste cloud realizado
nesta revisão. [Estado operacional](operational-evidence.md): Lambda fechada com
concorrência zero; nenhuma chamada de autenticação foi disparada nesta auditoria.

## CPF → Lambda → PostgreSQL → JWT → API

```mermaid
sequenceDiagram
  actor C as Cliente sintético / operador IAM
  participant G as HTTP API Gateway (fechado)
  participant L as Lambda handler / AuthenticationService
  participant S as Secrets Manager via endpoint privado
  participant D as PostgreSQL / auth.customer_identity_v2
  participant J as RS256Signer
  participant A as API Java protegida
  participant K as JWKS HTTPS confiável ou PEM configurado
  Note over C,G: POST /auth/token é contrato HTTP, endpoint desabilitado e AWS_IAM
  Note over C,L: Demonstração futura requer janela sintética autorizada e InvokeFunction IAM
  C-->>L: Evento payload 2.0 com CPF canônico (invocação controlada)
  L->>L: Validar JSON, 1 KiB, 11 dígitos e verificadores
  alt Entrada inválida
    L-->>C: 400 INVALID_REQUEST
  else Entrada válida
    L->>S: GetSecretValue AWSCURRENT (configuração/cache do handler)
    S-->>L: Credencial SQL, chave privada, CA e kid (somente memória/configuração)
    L->>D: SELECT customer_id, active WHERE cpf = parâmetro LIMIT 2
    Note over L,D: TLS verify-full, login oficina_lambda, somente SELECT na V2
    D-->>L: Zero, uma ou mais identidades
    L->>L: Exigir exatamente uma, active true e UUID na allowlist sintética
    alt Ausente, ambígua, inativa ou fora da allowlist
      L-->>C: 401 AUTHENTICATION_FAILED uniforme
    else Banco/segredo indisponível
      L-->>C: 503 TEMPORARILY_UNAVAILABLE, sem token
    else Identidade elegível
      L->>J: Assinar sub UUID, role CUSTOMER, iss, aud, iat, nbf, exp
      J-->>L: JWT RS256 com kid, TTL 900 segundos
      L-->>C: 200 access_token, no-store, sem CPF nos claims
      C->>A: Bearer JWT em rota CUSTOMER
      A->>K: Obter chave confiável conforme cadeia / cache
      A->>A: Validar RS256, kid, assinatura, iss/aud, tempos e role
      A->>D: Consultar OS e conferir proprietário pelo UUID autenticado
      alt OS própria na rota por UUID
        A-->>C: 200 resposta mínima
      else OS alheia ou inexistente na rota por UUID
        A-->>C: 404
      end
    end
  end
```

A view V2 publica `customer_id`, `cpf`, `active`; o adapter lê somente UUID e active.
`active` vem de `auth.customer_access`, default false; `customers.active` tem outra
semântica. CPF normalizado ambíguo suprime a identidade. A assinatura não consulta
serviço KMS: usa chave RSA do segredo composto. O handler reutiliza serviço em cache;
não se pressupõe leitura de segredo em cada request.

`/api/v1/customer/**` usa JWKS e principal `CustomerIdentity`. As rotas por código
`/api/v1/public/work-orders/**` **também exigem autenticação**, usam configuração PEM
e autorização de método por ownership (403 para alheia/inexistente nesse controle).
O nome `public` não significa acesso anônimo. ADMIN usa login próprio e HMAC,
sem promoção de CUSTOMER para ADMIN ou fallback entre algoritmos.

Fontes: [auth/handler.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/handler.py), [auth/request.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/request.py),
[auth/postgres.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/postgres.py), [auth/cloud.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/cloud.py),
[auth/jwt.py](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/auth/jwt.py), [cadeia JWKS](../../src/main/java/br/com/fiap/techchallenge/oficina/configuration/CustomerSecurityConfig.java),
[segurança das demais rotas](../../src/main/java/br/com/fiap/techchallenge/oficina/configuration/SecurityConfig.java),
[controller por código](../../src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web/PublicWorkOrderController.java).

## Abertura e processamento de OS

```mermaid
sequenceDiagram
  actor ADM as Atendente ADMIN
  actor CLI as CUSTOMER proprietário
  participant W as WorkOrderController / PublicWorkOrderController
  participant U as Casos de uso
  participant O as Domínio WorkOrder
  participant DB as Repositórios PostgreSQL
  participant N as Outbox / SMTP local
  ADM->>W: POST /api/v1/admin/work-orders
  W->>U: CreateWorkOrderUseCase.create
  U->>DB: Resolver/criar cliente por documento e veículo por placa
  Note over U,DB: TransactionPort.required, veículo de outro cliente gera conflito
  U->>O: Criar RECEIVED, adicionar serviços/peças, calcular orçamento
  Note over O: Se houver itens, pode ir direto para WAITING_APPROVAL
  U->>DB: Salvar OS, itens e eventos de histórico na transação
  DB-->>ADM: 201 com id e code
  opt OS recebida sem itens
    ADM->>W: POST /{id}/diagnosis/start
    W->>U: StartDiagnosisUseCase.start
    U->>O: IN_DIAGNOSIS
    ADM->>W: POST /{id}/diagnosis/notes e /{id}/items
    W->>U: UpdateDiagnosisUseCase.update / AddWorkOrderItemsUseCase.add
    U->>O: Atualizar notas e orçamento, WAITING_APPROVAL
    U->>DB: Persistir alterações e transições
  end
  opt Notificação local configurada
    ADM->>W: POST /{id}/budget/notify
    W->>U: NotifyBudgetUseCase.notifyBudget
    U->>DB: Enfileirar notification_outbox PENDING na transação
    N->>DB: ProcessNotificationOutboxUseCase, claim PROCESSING
    N->>N: SMTP fora da requisição, SENT ou FAILED
  end
  CLI->>W: POST /public/work-orders/{code}/budget/approve ou reject
  W->>W: JWT + ownership, documento no body
  W->>U: ExternalBudgetDecisionUseCase.approve/reject
  U->>DB: Aprovação: Idempotency-Key opcional, lock OS/peças
  alt Aprovação
    U->>O: approveBudget, reservar/baixar estoque pendente
    O-->>U: IN_EXECUTION
    U->>DB: Salvar peças, OS, histórico e idempotência na transação
  else Recusa inicial
    U->>O: rejectBudget
    O-->>U: BUDGET_REJECTED
    U->>DB: Salvar OS e histórico, sem baixa de estoque
  end
  opt OS aprovada em execução
    ADM->>W: POST /{id}/finish
    W->>U: FinishWorkOrderUseCase.finish
    U->>O: FINALIZED
    U->>DB: Persistir OS e histórico
    ADM->>W: POST /{id}/deliver
    W->>U: DeliverWorkOrderUseCase.deliver
    U->>O: DELIVERED
    U->>DB: Persistir OS e histórico
  end
```

As rotas abreviadas `/{id}` pertencem a `/api/v1/admin/work-orders`; a decisão por
código pertence a `/api/v1/public/work-orders`. ADMIN também pode aprovar via
`DecideBudgetUseCase.approve` em `POST /api/v1/admin/work-orders/{id}/approve`.
Recusa após início da execução viola regra de domínio; não é cancelamento genérico.
Inclusão de novos itens durante execução pode exigir nova aprovação. Um replay
idempotente aprovado retorna o estado corrente sem repetir baixa; hash divergente
ou registro PROCESSING/FAILED provoca conflito. A checagem de ownership ocorre
antes do caso de uso. Outbox e idempotência não são serviços externos nem brokers.

Fontes: [casos de uso reais](../../src/main/java/br/com/fiap/techchallenge/oficina/application/usecase),
[WorkOrder](../../src/main/java/br/com/fiap/techchallenge/oficina/domain/model/workorder/WorkOrder.java),
[controllers](../../src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web),
[migrations](../../src/main/resources/db/migration). SMTP/outbox automática de staging
estão desabilitados na [configuração staging](../../src/main/resources/application-staging.yml).
