# Métricas de negócio — contrato para dashboards

PR independente de logs/tracing. Sem migrations, alteração de contratos HTTP,
regras de negócio, autenticação, README geral ou manifests. Reusa histórico V9,
WorkOrder.created_at e métricas de outbox; o endpoint de métricas de tempo de
execução existente permanece intacto. Esse endpoint mede started_at → finished_at,
não a permanência em cada status, e não é chamado pelo coletor.

## Catálogo Prometheus

Nomes abaixo são os nomes exportados em `/actuator/prometheus`. Labels adicionados
por scrape (`job`, `instance`, `pod`, etc.) dependem da infraestrutura. A aplicação
adiciona apenas os labels enumerados abaixo; nunca UUID, CPF, código da OS, token,
texto de erro, destinatário ou data dinâmica.

| Nome | Tipo | Labels da aplicação | Unidade e significado |
|---|---|---|---|
| `oficina_work_orders_created_today` | gauge | nenhum | OS criadas no dia civil atual em America/Sao_Paulo, [00:00, próxima 00:00); **não** últimas 24h |
| `oficina_work_order_status_completed_intervals` | gauge | `status` | quantidade de intervalos concluídos em todo o histórico real disponível |
| `oficina_work_order_status_completed_duration_seconds` | gauge | `status` | soma dos segundos desses intervalos concluídos |
| `oficina_work_order_status_open_intervals` | gauge | `status` | quantidade de intervalos ainda abertos com entrada real conhecida |
| `oficina_work_order_status_open_age_seconds` | gauge | `status` | soma das idades dos intervalos abertos, no instante do snapshot |
| `oficina_notification_outbox_pending` | gauge existente | nenhum | mensagens PENDING no banco |
| `oficina_notification_outbox_processing` | gauge existente | nenhum | mensagens PROCESSING no banco |
| `oficina_notification_outbox_sent` | gauge existente | nenhum | mensagens SENT no banco |
| `oficina_notification_outbox_failed` | gauge existente | nenhum | mensagens FAILED no banco, **não** contador de tentativas |
| `oficina_notification_processing_failures_total` | counter | `stage=attempt\|batch` | attempt: tentativas que falharam nos lotes retornados; batch: execução que lançou RuntimeException |
| `oficina_business_metrics_refresh_failures_total` | counter | nenhum | atualizações de snapshot que falharam |
| `oficina_business_metrics_snapshot_timestamp_seconds` | gauge | nenhum | timestamp Unix da última coleta bem-sucedida (segundos) |
| `http_server_requests_seconds_count` | timer count existente | method, uri, status, outcome, exception | requisições; filtrar 5xx para falhas HTTP reais |

`status`: RECEIVED, IN_DIAGNOSIS, WAITING_APPROVAL, IN_EXECUTION,
BUDGET_REJECTED, FINALIZED, DELIVERED (sete valores fixos).

Um intervalo concluído liga h → n da mesma OS, com event_order consecutivo,
`n.previous_status=h.new_status` e timestamps não decrescentes. Duração atribuída
à permanência em `h.new_status`. Reentradas em WAITING_APPROVAL/IN_EXECUTION são
intervalos separados. Não inferir início a partir de updated_at, created_at ou do
primeiro previous_status de uma OS legada. Histórico ausente não vira duração zero.
Uma categoria sem intervalos tem contagem e soma zero; a média deve ficar sem dado.

Aberto = último evento conhecido, consistente com status atual, entrada não futura.
DELIVERED e BUDGET_REJECTED são terminais e não acumulam idade aberta; FINALIZED
permanece aberto enquanto aguarda entrega. Idade aberta não entra no denominador
nem na soma da média concluída. Não são histogramas/percentis e não há histórico
sintético. Gauges de duração são agregados do banco, não counters: **não usar rate
ou increase** sobre eles.

## Coleta, cache e múltiplas réplicas

Uma transação somente leitura REPEATABLE_READ executa quatro consultas agregadas:
dia, intervalos concluídos, intervalos abertos e outbox por status. Timeout da
transação/queries de 2 s. Atualização após 1 s e a cada 60 s após terminar a anterior,
em scheduler próprio de uma thread; não bloqueia o scheduler da outbox. Sem locks
escritores. Memória limitada a um snapshot com sete status e quatro estados de
outbox; nunca carrega entidades, corpos ou histórico completo em Java.

Scrapes fazem somente leitura em memória, incluindo as quatro métricas de outbox
que antes consultavam o banco a cada scrape. Até a primeira coleta, ou após 120 s
sem sucesso, os valores de negócio são NaN. Timestamp continua mostrando a última
coleta; falhas incrementam counter. À meia-noite local, contagem do dia anterior
fica NaN até a próxima coleta (não aparece como valor do novo dia).

Cada réplica lê o mesmo banco: **deduplicar gauges com max; nunca somar réplicas**.
Selecionar um único ambiente/banco via job/namespace/cluster antes de agregar.
Os snapshots entre pods podem diferir por até um ciclo; max é uma visão eventual,
não uma leitura transacional distribuída. Counters de eventos locais devem usar
`sum(rate(...))`/`sum(increase(...))`, pois representam trabalho de cada processo.

V1 não tem índice em created_at e o histórico pode crescer: leituras periódicas
podem varrer tabelas. O timeout evita insistência indefinida; acima da capacidade,
o painel fica sem dado e o alerta dispara. Não há benchmark de carga staging nem
proposta de índice nesta frente. A aquisição de conexão segue os limites existentes
do pool. Não aumentar frequência sem medir o banco.

## PromQL sugerido

Substituir `job="oficina-api"` pelo seletor real, sempre restrito a um ambiente/banco.

```promql
# Volume do dia civil atual; não usar increase(...[24h]) neste gauge.
max(oficina_work_orders_created_today{job="oficina-api"})

# Média de permanência CONCLUÍDA por status, em segundos (0/0 = sem dado).
max by (status) (oficina_work_order_status_completed_duration_seconds{job="oficina-api"})
/
max by (status) (oficina_work_order_status_completed_intervals{job="oficina-api"})

# Idade média ABERTA por status, em segundos, separada da média concluída.
max by (status) (oficina_work_order_status_open_age_seconds{job="oficina-api"})
/
max by (status) (oficina_work_order_status_open_intervals{job="oficina-api"})

# Falhas processadas, por etapa; não somar etapas como mensagens únicas.
sum by (stage) (increase(oficina_notification_processing_failures_total{job="oficina-api"}[1h]))

# Estoque atual de mensagens definitivamente falhadas, deduplicado.
max(oficina_notification_outbox_failed{job="oficina-api"})

# Falhas reais HTTP por segundo (4xx de validação não contam como 5xx).
sum(rate(http_server_requests_seconds_count{job="oficina-api",status=~"5.."}[5m]))

# Coleta falhando em alguma réplica.
sum(rate(oficina_business_metrics_refresh_failures_total{job="oficina-api"}[5m])) > 0

# Última coleta bem-sucedida de cada réplica está velha.
time() - oficina_business_metrics_snapshot_timestamp_seconds{job="oficina-api"} > 120
```

Configurar também alerta de ausência/NaN e `up == 0`: timestamp inicial é NaN, não
zero. Para tendência diária, usar painel temporal desse gauge; não prometer o total
fechado de dias passados se nenhum scrape capturou as últimas criações antes da
virada. Totais históricos diários exatos exigem outra fonte/consulta, fora do mínimo.

Counters de processamento começam na inicialização da versão instrumentada; não
reconstroem falhas passadas. Retries contam tentativas, não notificações únicas.
Se uma exceção aborta o lote antes de retornar o resultado, o counter `batch` sobe,
mas não há resultado confiável para contar todas as tentativas anteriores desse
lote. Não somar attempt + batch como um total de mensagens. Outbox continua sendo
a fonte do estado durável; counters podem perder eventos num crash antes do scrape.

## Aceite runtime e testes

1. Confirmar scrape por pod, selecionar um ambiente e esperar uma coleta (até 60 s).
2. Criar OS sintética: gauge diário sobe uma unidade após refresh, inclusive com
   duas réplicas quando consultado com `max` (não duplica).
3. Efetuar duas transições válidas: conferir soma/contagem do status anterior contra
   timestamps/event_order no histórico, usando acesso somente leitura autorizado.
   Enquanto não transicionar, a idade aberta cresce a cada coleta; a duração
   concluída não cresce. Status terminal não acumula idade aberta.
4. Em ambiente de teste autorizado, simular falha SMTP e processar a outbox: observar
   `stage=attempt` e estado PENDING/FAILED conforme retry. Falha de claim aborta lote
   e incrementa `stage=batch`. Não executar essa simulação no banco staging nesta frente.
5. Verificar catálogo/type/labels em `/actuator/prometheus`. Scrapes repetidos não
   alteram valores nem executam SQL. Falha de coleta conserva valor por até 120 s,
   depois NaN; recuperar a fonte restaura próximo snapshot.

Testes cobrem SQL PostgreSQL/migrations reais, limites do dia civil, rollback,
reentradas, legado sem histórico, terminais, cache/expiração/virada do dia e falhas
reais do serviço de outbox com adapter de envio simulado. Nenhum aceite staging ou
alteração no banco staging foi realizado.
