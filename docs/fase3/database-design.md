# Decisões de persistência e integridade

Fonte física: [modelo gerado de V1–V11](data-model.md). Fonte operacional:
[RDS observado](operational-evidence.md). O domínio exige transações entre orçamento,
estoque, OS e histórico; PostgreSQL oferece UUID, `numeric(12,2)`, FK/UNIQUE/CHECK,
locks e isolamento adequados. RDS delega operação do servidor e backups ao serviço,
reduzindo trabalho do projeto. Isso não elimina administração de schema, usuários,
capacidade, retenção, restauração e custo. Single-AZ não garante disponibilidade
entre AZs; snapshot configurado não equivale a restore testado.

## Integridade e concorrência

PKs UUID identificam entidades; FKs protegem cliente/veículo/itens/OS/histórico.
UNIQUE protege username, documento, placa, SKU, código da OS, sequência do histórico
e par operação/chave idempotente. CHECK limita enumerações e regras de histórico.
V1 não cria CHECK genérico de preço/estoque positivo: essas regras estão no domínio;
não atribuir ao banco garantias ausentes. Tipo numeric evita arredondamento binário
para valores monetários, mas precisão/escala impõem limites de magnitude.

`CreateWorkOrderService` resolve cliente/veículo e grava a OS na mesma transação.
A aprovação obtém locks de OS/peças antes da baixa; `parts.version` apoia detecção
optimista de atualizações concorrentes. A chave UNIQUE da idempotência impede duas
linhas para a mesma operação/chave. A outbox persiste intenção de notificação;
claim com lock e SKIP LOCKED permite concorrência, mas não prova entrega SMTP
exatamente uma vez. Falhas entre envio e confirmação podem exigir reconciliação.

## Histórico auditável da OS

V9 registra `previous_status`, `new_status`, `occurred_at`, `event_order` por OS.
A chave UNIQUE `(work_order_id,event_order)` ordena os eventos; CHECK exige ordem
positiva, mudança real e criação RECEIVED no primeiro evento com status anterior
nulo. O domínio registra transições e a persistência salva-as junto da OS. Não há
trigger nem event sourcing completo: o estado corrente continua em `work_orders`.
OS anteriores à V9 ficam sem eventos inventados. A role da API só recebe SELECT/INSERT
no histórico; isso impede reescrita por essa role, mas não torna dados imutáveis
contra administrador/migrator. Não há ator/IP da transição no schema: não inventá-los.

## Privilégios por finalidade

| Identidade | Permissão definida | Limite |
|---|---|---|
| Administrador bootstrap | Criar roles e grants | Uso operacional separado; não é credencial da API/Lambda |
| `oficina_migrator` | DDL e ownership das migrations | Job separado; Flyway desligado no runtime staging |
| `oficina_api` | DML nas tabelas listadas em grants | app_users SELECT/INSERT; histórico SELECT/INSERT; sem auth.customer_access/Flyway/DDL |
| `oficina_lambda` → `oficina_auth_reader` | USAGE auth e SELECT só na V2 | Sem V1, tabelas-base, TEMP/DDL ou escrita |
| `auth_readonly` legado | Contrato V1 por bootstrap separado | Não conceder ao login estrito como fallback |

Fontes: [bootstrap de roles](../../scripts/staging/01-bootstrap-roles.sql),
[grants runtime](../../scripts/staging/02-runtime-grants.sql),
[reader V2](../../scripts/bootstrap-auth-reader.sql),
[reader V1 legado](../../scripts/provision-auth-readonly.sql).
O esquema e os testes foram auditados; privilégios efetivos no RDS ainda requerem
consulta operacional sanitizada com as identidades reais. O acesso V2 é explícito;
inativar `customers.active` não revoga `auth.customer_access.active` nem JWT já emitido.

## Índices: benefício e custo

- Índices de customer_id/vehicle_id/status ajudam filtros e associações de OS;
  não há índice composto específico para toda a ordenação da fila operacional.
  Medir com EXPLAIN (ANALYZE, BUFFERS) em base sintética representativa antes de propor.
- UNIQUE já cria índices para documento, placa, código, SKU e operação/chave.
  V1/V6 também declaram índices explícitos nessas mesmas chaves: há redundância
  potencial e custo adicional de escrita, WAL e armazenamento. Não foram removidos.
- V9 aproveita o índice único composto para consultar histórico ordenado por OS;
  UNIQUE também impede sequência repetida, mas não exige sequência sem lacunas.
- V11 tem índice parcial de expressão, **não único**, para detectar colisões de CPF
  sem impedir upgrade com dados legados. Não assegura unicidade normalizada.
- Outbox tem status e status/created_at; avaliar sobreposição, seletividade, volume
  de mensagens e plano real antes de manutenção. Não há benchmark nesta auditoria.

Custos e riscos: [trade-offs](trade-offs.md). A estratégia de atualização preserva
checksums V1–V11; evolução futura usa nova migration revisada. Rollback da imagem
não reverte schema automaticamente. Testes de upgrade, retenção/expurgo de outbox e
histórico e recuperação RDS continuam responsabilidades operacionais.
