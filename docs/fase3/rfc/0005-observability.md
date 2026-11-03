# RFC 0005 — Observabilidade

Data: 2026-11-03. Autor da consolidação: documentação do Grupo 97.
Status: reconstrução auditável das escolhas implementadas e propostas remanescentes;
não registra aprovação retroativa. Escopo: staging acadêmico.

## Problema

Demonstrar saúde, desempenho e capacidade sem confundir métricas de HPA com monitoramento histórico.

## Solução e estado

Actuator/Micrometer e Prometheus/Grafana locais implementados; Metrics Server operacional no K3s. Propor implantação cloud privada de Prometheus/Grafana, retenção limitada e alertas após orçamento/revisão. Tracing fica explicitamente não implementado/comprovado.

## Alternativas e trade-offs

CloudWatch pode centralizar parte das métricas/logs com cobrança própria. OpenTelemetry com coletor/backend atenderia tracing, mas não há instrumentação/exportador comprovado; um nome em diagrama não constitui implementação.

## Critérios de aceite e evolução

Aceitar somente com targets UP, séries de taxa/erro/latência/JVM, dashboards, teste de alertas, retenção e teste negativo de exposição. Nunca incluir CPF/JWT/credenciais em labels/logs. Atual permitAll de prometheus exige proteção antes de exposição pública.

Mudanças futuras precisam de PR específica, validação operacional e revisão do impacto;
esta RFC não executa mudanças nem fecha pendências por declaração.

## Fontes

[Arquitetura](../deployment-architecture.md), [limites](../trade-offs.md), [Compose](../../../docker-compose.yml).

## Lacunas perante o edital

O dashboard local contém status, taxa/latência HTTP, memória JVM, CPU do processo e
outbox pendente/falha. Não contém o conjunto completo exigido: volume diário de OS,
tempo por status e falhas de integração com alerta comprovado. O endpoint de tempo
médio de execução não equivale a dashboard por status. `ApiExceptionHandler` inclui
traceId de erro/X-Correlation-Id validado, mas logback usa formato textual; isso não
comprova logs JSON correlacionados em todas as requisições nem tracing distribuído.
Fontes: [dashboards](../../../observability/grafana/dashboards),
[logback](../../../src/main/resources/logback-spring.xml),
[handler de erros](../../../src/main/java/br/com/fiap/techchallenge/oficina/adapters/in/web/ApiExceptionHandler.java).
