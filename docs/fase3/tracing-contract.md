# Contrato de logs e tracing — Fase 3

Java 21, Spring Boot 3.5.14, Micrometer 1.15.11, Micrometer Tracing 1.5.11,
OpenTelemetry 1.49.0, Logback 1.5.32. Uma instrumentação: observações Spring MVC →
Micrometer Tracing → OpenTelemetry SDK → OTLP HTTP/protobuf. Sem Java agent.
Versões herdadas do BOM, sem upgrade da plataforma.

## Handoff para Tempo privado

Configuração externa proposta para staging (esta PR não altera manifests):

```text
MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED=true
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://oficina-tempo:4318/v1/traces
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
```

O hostname é proposto: a sessão de infraestrutura deve substituí-lo pelo DNS
privado do Service, incluindo namespace se necessário. Porta **4318**, transporte
**HTTP/protobuf**, endpoint completo **/v1/traces** (não gRPC/4317).
`service.name=oficina-api`, derivado de `spring.application.name`; não definir
`OTEL_SERVICE_NAME` conflitante. Sem exposição pública do receiver. Acesso de rede
API → Tempo é responsabilidade da sessão de infraestrutura.

Por padrão, exportação desabilitada e amostragem 10%; staging deve fornecer as
variáveis acima para aceite. Depois, ajustar sampling ao volume/custo. Trace IDs
podem existir em logs mesmo quando o span não é amostrado/exportado.
Fila limitada a 2048 spans, lotes de 512 a cada 5 s, timeout HTTP 2 s, conexão 1 s,
limite do processador 3 s. Exportação assíncrona; falhas descartam spans após os
limites do SDK e não afetam readiness/liveness. Não há armazenamento durável de
traces na API. A configuração é Spring Boot, não variáveis genéricas de Java agent.

## JSON e privacidade

Console e diagnóstico restrito usam JSON Logstash nativo do Boot, um evento por
linha, com `traceId`/`spanId` provenientes do contexto real de tracing. Sem span
ativo, esses campos não são inventados. O campo público `ErrorResponse.traceId`
permanece com o mesmo nome/tipo e passa a refletir o trace real (null fora de
contexto HTTP instrumentado). `X-Correlation-Id` não é usado como trace ID.

O log de conclusão registra apenas status e template de rota ou `[unmatched]`;
não registra URL concreta, query, headers, JWT, corpos, senhas ou documento.
Traces HTTP omitem URL bruta; erros recebidos exportam somente classe da exceção,
sem mensagem original, causa ou stack. Sem baggage configurado. Console exclui
stack_trace; diagnóstico mantém somente os locais de aplicação já sanitizados.
Outbox registra classe do erro, não sua mensagem. SQL exception helper é silenciado
porque detalhes do PostgreSQL podem incluir dados pessoais. Não habilitar logs
DEBUG de payload/SQL em staging. O armazenamento preexistente de `last_error` na
outbox não é alterado nesta frente.

## Aceite runtime (operador da sessão de infraestrutura)

1. Configurar as três variáveis e confirmar receiver privado HTTP habilitado.
2. Executar `curl -sS http://<api>/actuator/health`; deve responder 200 e gerar um
   log JSON `HTTP request completed` com traceId hexadecimal de 32 caracteres e
   spanId de 16. Aguardar até 5 s e procurar esse traceId no Tempo; verificar
   service.name e span de servidor com mesmo spanId/status/rota.
3. POST `/api/v1/auth/login` com `{"username":"","password":"teste-descartavel"}`
   e Content-Type JSON: esperar 400; comparar `traceId` do erro com log/Tempo.
   Enviar um X-Correlation-Id UUID diferente não deve substituir esse valor.
4. Confirmar ausência de senha, corpo, Authorization e query nos logs e atributos
   do trace. Usar exclusivamente dados sintéticos nesse aceite.
5. Tornar o receiver indisponível no ambiente de teste do operador e repetir a
   chamada de saúde: API continua respondendo 200; restaurar receiver e confirmar
   novos traces. Não esperar recuperação de spans descartados durante a falha.

`TracingIntegrationTest` executa HTTP real + receptor OTLP local, verifica o encoder
real de console, IDs no protobuf, service.name, privacidade, erro 503 e conexão
recusada. `mvn -B clean verify` também verifica contratos, segurança, histórico,
outbox, PostgreSQL V1–V11 e cobertura de domínio. Não foi realizado deploy ou aceite
no Tempo de staging por esta frente.
