# Fase 3 — Customer status e histórico da OS

Base: `main`, incluindo `dd28f8495dda1d51c494d1b1f590dc11cbe20c2c` (merge da PR #1).

## Cliente

`Customer` nasce com `active=true`. V8 adiciona a coluna `boolean not null default true`, mantendo todos os clientes existentes ativos. O mapper restaura o status; editar nome/contatos não reativa o cliente.

- `PUT /api/v1/admin/customers/{id}/status`, corpo `{"active":false}` ou `{"active":true}`, retorna o cliente atualizado. Campo obrigatório.
- `DELETE /api/v1/admin/customers/{id}` mantém o HTTP 204, mas passa a inativar. Repetições são seguras. Clientes, veículos e OS continuam consultáveis pelos administradores.
- Respostas administrativas recebem o campo aditivo `active`. Os demais campos e rotas permanecem.
- Ambas as operações exigem a autorização ADMIN já definida na PR #1. Não há endpoint público novo.
- `CustomerRepositoryPort.findByDocumentNumberAndActive` fornece a consulta interna para uma integração futura. O adapter normaliza a pontuação do CPF/CNPJ. A unicidade e o índice existentes em `document_number` já tornam a busca seletiva; não foi criado índice redundante em boolean.
- Inativar ainda não altera regras de abertura de OS nem a consulta pública existente; a futura autenticação deverá exigir `active=true`.
- Não foram adicionados logs de documentos, contatos ou payloads.

## Histórico e transação

O agregado registra `WorkOrderStatusTransition` (UUID estável, estado anterior, novo estado e instante com offset) em cada mudança real. A construção registra `null -> RECEIVED`; a restauração não gera eventos. A lista contém apenas eventos desta instância, não todo o histórico persistido.

O adapter salva a OS e os eventos na mesma transação JPA; `@Transactional` no save participa do `TransactionPort` existente. O objeto retornado é restaurado sem eventos pendentes. Salvar novamente uma instância já identificada usa os UUIDs para evitar duplicações. Não há publicação assíncrona nem transação independente. Falha no histórico reverte a atualização da OS; rollback posterior também desfaz ambos.

As consultas detalhadas agora adquirem o mesmo lock pessimista da OS já usado pela aprovação com estoque, antes de montar o agregado. Isso serializa os fluxos de alteração e impede decisões concorrentes sobre o mesmo estado antigo. Mantém-se a ordem OS → peças e a idempotência existente. Como contrapartida, leituras detalhadas também podem aguardar uma transação de escrita. As regras de estoque e orçamento permanecem no domínio.

V9 cria `work_order_status_history`: PK por evento; FK sem cascade para a OS; sequência positiva única por OS; estados válidos; proibição de transição para o mesmo estado; criação apenas como primeiro evento em RECEIVED. O índice único `(work_order_id,event_order)` também atende às leituras ordenadas. A sequência preserva a ordem mesmo quando instantes empatam. Ciclos legítimos de execução/aprovação são permitidos.

`WorkOrderStatusHistoryPort` permite ler os eventos sem expor JPA/Spring ao domínio ou à aplicação. Escritas devem passar pelo adapter de OS; SQL externo direto em `work_orders` não gera eventos automaticamente. Esta tabela não constitui auditoria de autoria administrativa.

## Legado e indicadores

V1–V7 permanecem intactas. Nenhum evento é fabricado para OS anteriores à V9: estado atual, `created_at`, `started_at` e `finished_at` não permitem reconstruir todas as transições. A primeira alteração posterior passa a registrar o estado anterior conhecido e o novo estado, com o instante real da alteração. Ausência de evento de criação indica cobertura histórica parcial.

Para cada evento, o intervalo até o próximo evento pertence ao `new_status`:

| Etapa | Intervalo medido |
| --- | --- |
| Diagnóstico | entrada em IN_DIAGNOSIS até a próxima transição |
| Execução | cada entrada em IN_EXECUTION até a próxima transição; somar os intervalos, excluindo WAITING_APPROVAL |
| Finalização | entrada em FINALIZED até DELIVERED (tempo aguardando entrega) |

RECEIVED mede espera inicial; WAITING_APPROVAL mede espera de aprovação. BUDGET_REJECTED e DELIVERED são terminais. Não assumir tempo zero para etapas não observadas. Intervalos abertos não entram na média de etapas concluídas. Para uma média por OS, somar intervalos da mesma etapa por OS antes de calcular a média. OS sem histórico completo devem ser excluídas de indicadores de ciclo completo ou apresentadas separadamente como amostra parcial.

Exemplo de intervalos fechados, sem dados pessoais:

```sql
with intervals as (
    select work_order_id, new_status, occurred_at,
           lead(occurred_at) over (partition by work_order_id order by event_order) as ended_at
    from work_order_status_history
)
select work_order_id, new_status,
       sum(extract(epoch from ended_at - occurred_at)) as seconds
from intervals
where ended_at is not null
  and new_status in ('IN_DIAGNOSIS', 'IN_EXECUTION', 'FINALIZED')
group by work_order_id, new_status;
```

O endpoint existente de média de execução mantém sua semântica baseada em started_at/finished_at, que inclui esperas intermediárias. Esta PR fornece os dados para indicadores por etapa, sem alterar esse contrato. Os instantes dos eventos vêm do relógio da aplicação; os nós devem manter relógios sincronizados. A sequência, e não o timestamp, define a ordem dos eventos.

## Validação

`mvn clean verify`: **BUILD SUCCESS**, 137 testes, zero falhas/erros/ignorados, em 31/10/2026. Java 21, Testcontainers e PostgreSQL 16; gate JaCoCo aprovado. Testes cobrem criação ativa, ativação/inativação e vínculos preservados, autorização ADMIN, upgrade real V7→V9, histórico de criação/transições, saves repetidos, transições inválidas, rollback após flush, falha na gravação de histórico, concorrência e repetição idempotente via REST. A suíte anterior e o gate de cobertura JaCoCo também são executados.

Sem Lambda, API Gateway, infraestrutura AWS, deploy ou merge automático.

## Arquivos alterados

- `docs/fase3/pr2-customer-status-workorder-history.md`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web/CustomerController.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web/dto/customer/CustomerResponse.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web/mapper/WebDtoMapper.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/CustomerPersistenceAdapter.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/WorkOrderPersistenceAdapter.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/WorkOrderStatusHistoryAdapter.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/entity/CustomerJpaEntity.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/entity/WorkOrderStatusHistoryJpaEntity.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/mapper/CustomerPersistenceMapper.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/repository/SpringDataCustomerRepository.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/persistence/repository/SpringDataWorkOrderStatusHistoryRepository.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/application/port/in/ManageCustomersUseCase.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/application/port/in/result/CustomerResult.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/application/port/out/CustomerRepositoryPort.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/application/port/out/WorkOrderStatusHistoryPort.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/application/usecase/ManageCustomersService.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/application/usecase/mapper/ApplicationResultMapper.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/domain/model/customer/Customer.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/domain/model/workorder/WorkOrder.java`
- `src/main/java/br/com/fiap/techchallenge/oficina/domain/model/workorder/WorkOrderStatusTransition.java`
- `src/main/resources/db/migration/V8__add_customer_active.sql`
- `src/main/resources/db/migration/V9__create_work_order_status_history.sql`
- `src/test/java/br/com/fiap/techchallenge/oficina/api/AdminRbacSecurityTest.java`
- `src/test/java/br/com/fiap/techchallenge/oficina/api/WorkOrderFlowIntegrationTest.java`
- `src/test/java/br/com/fiap/techchallenge/oficina/domain/CustomerDomainTest.java`
- `src/test/java/br/com/fiap/techchallenge/oficina/domain/WorkOrderDomainTest.java`
- `src/test/java/br/com/fiap/techchallenge/oficina/integration/CustomerStatusAndHistoryIntegrationTest.java`
- `src/test/java/br/com/fiap/techchallenge/oficina/integration/DatabaseMigrationIntegrationTest.java`
