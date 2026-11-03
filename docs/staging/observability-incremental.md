# Atualização incremental: Tempo e negócio

Base: main após PR #13. Contratos exclusivos: [negócio PR #16](https://github.com/ppdssdev/tech-challenge-oficina/blob/ff3468a9cf45360f71df382de6a75ffed9fca7d9/docs/fase3/business-metrics-contract.md) e [tracing PR #15](https://github.com/ppdssdev/tech-challenge-oficina/blob/4147de1b26fa4786328aee06ebda326aa96b30a9/docs/fase3/tracing-contract.md).
Ambas precisam estar na versão da API para aceite completo; esta entrega não altera a API.

`upgrade` exige Prometheus/Grafana existentes, seleciona somente seus Deployments e
ConfigMaps, mais os recursos novos do Tempo. Não aplica Secret, PVC Prometheus,
Services existentes ou RBAC. ConfigMaps com hash provocam rollout dos dois componentes.
Não executar `install` nem `create-admin-secret.py` no staging existente.
Gauges compartilhados usam max; duração concluída e idade aberta têm painéis separados.
Alertas são avaliados no Prometheus: **não existe canal externo/Alertmanager**.
Falhas attempt/batch, HTTP 5xx, refresh, ausência por réplica/etapa, NaN, snapshot
antigo e targets ausentes/down têm testes sintéticos promtool. Nenhuma notificação
real ou falha SMTP deve ser provocada para aceite.
A descoberta Ready não permite inferir uma réplica que desapareceu enquanto outras
continuam saudáveis; o aceite de quantidade continua no check-targets.py/HPA.

Tempo 2.10.5 fixado por digest amd64: processo único, ClusterIP, NetworkPolicy de
entrada apenas API:4318 e Grafana:3200 no mesmo namespace. Requer enforcement de
NetworkPolicy do K3s. Sem autenticação própria; acesso administrativo por SSM.
Configuração conforme [documentação Tempo 2.10](https://grafana.com/docs/tempo/v2.10.x/configuration/).
OTLP HTTP/protobuf: http://oficina-tempo:4318/v1/traces; datasource: http://oficina-tempo:3200.
Requests 50m/128Mi, limites 500m/384Mi, PVC local-path 2Gi, retenção 24h,
ingestão 64KiB/s (burst 128KiB), trace até 1MiB, 1000 traces ativos.
Retenção e PVC local-path não são quota física: WAL/compactação exigem margem;
monitorar disco da EC2. Sem HA/backup. Nenhum PVC é removido no rollback.
Grafana já usa emptyDir: seu rollout perde personalizações locais não provisionadas;
exporte essas personalizações antes da operação se existirem. Secret e dados
persistentes de Prometheus/API permanecem preservados.

## UBUNTU LOCAL — preparar e validar

Requisitos: Git, Docker, kubectl, Python3/PyYAML e shellcheck instalados.
Copie o SHA final da PR, sem usar main implicitamente:

```bash
export OBS_SHA='<SHA_FINAL_DA_PR>'
git fetch origin codex/staging-tempo-business
git worktree add --detach /tmp/oficina-observability-review "$OBS_SHA"
cd /tmp/oficina-observability-review
bash scripts/staging/observability/validate.sh
```

Abra a sessão SSM pelo mecanismo já utilizado. Não alterar regras de rede,
Terraform, instância, Secret ou deployment da API para executar este roteiro.

## EC2 VIA SSM — instalar incrementalmente

Use um checkout acessível ao operador, sem pressupor /opt. Os comandos abaixo
criam um worktree separado a partir desse checkout. PyYAML deve estar disponível
no Python usado com sudo; se faltar, preparar a dependência antes da operação.

```bash
export CHECKOUT='<caminho-do-checkout-existente>'
export OBS_SHA='<SHA_FINAL_DA_PR>'
export OBS_WORKTREE="$HOME/oficina-observability-$OBS_SHA"
git -C "$CHECKOUT" fetch origin codex/staging-tempo-business
git -C "$CHECKOUT" worktree add --detach "$OBS_WORKTREE" "$OBS_SHA"
cd "$OBS_WORKTREE"
sudo python3 -c 'import yaml'
sudo k3s kubectl -n oficina-staging get deployments,pvc
sudo k3s kubectl -n oficina-staging get secret oficina-observability-grafana-admin -o name
sudo env KUBECTL="$OBS_WORKTREE/scripts/staging/observability/k3s-kubectl.sh" \
  EXPECTED_API_PODS=auto bash scripts/staging/observability/install.sh upgrade
```

O script faz dry-run no servidor, preflight da API, snapshot e rollback automático
em falha. Registre o caminho `Snapshot para rollback: ...` impresso. O backup
manual `install.sh.before-empty-json-fix` deve ser preservado no checkout original;
a correção agora só aceita zero bytes com exit 0, sem esconder erro do kubectl,
JSON inválido ou whitespace. Não substituir o script novo pelo backup antigo.

## EC2 VIA SSM — aceite

```bash
sudo k3s kubectl -n oficina-staging rollout status deployment/oficina-observability-tempo --timeout=300s
sudo k3s kubectl -n oficina-staging get svc oficina-tempo
sudo k3s kubectl -n oficina-staging get networkpolicy oficina-observability-tempo
sudo k3s kubectl -n oficina-staging port-forward --address 127.0.0.1 service/oficina-tempo 13200:3200
```

Em outra sessão SSM: `curl --fail http://127.0.0.1:13200/ready`.
Pelo túnel Grafana existente, verificar datasource Tempo e painel
`Oficina staging — Negócio`. Não usar 4318 como URL do datasource.
Para consultar regras, em outra sessão:

```bash
sudo k3s kubectl -n oficina-staging port-forward --address 127.0.0.1 service/oficina-observability-prometheus 19090:9090
```

Em outra sessão: `curl --fail http://127.0.0.1:19090/api/v1/rules` e
`curl --fail http://127.0.0.1:19090/api/v1/targets`. Confirmar regras carregadas,
health=ok e targets individuais UP. Enquanto PR #16 não estiver implantada,
alertas de métricas ausentes são esperados. Testes de firing/recovery são locais.

Trace **real da API**, após implantação/configuração autorizada separadamente da PR #15:
`MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED=true`,
`MANAGEMENT_OTLP_TRACING_ENDPOINT=http://oficina-tempo:4318/v1/traces`,
`MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0` para aceite.
Esta entrega não aplica essas variáveis. Encaminhe a API em sessão separada:

```bash
sudo k3s kubectl -n oficina-staging port-forward --address 127.0.0.1 service/oficina-api 18080:8080
```

Em outra sessão SSM:

```bash
curl --fail http://127.0.0.1:18080/actuator/health
sudo k3s kubectl -n oficina-staging logs -l app=oficina-api --since=2m --tail=100 --prefix
export TRACE_ID='<traceId hexadecimal de 32 caracteres do log desta requisicao>'
sleep 6
curl --fail -H 'Accept: application/json' "http://127.0.0.1:13200/api/traces/$TRACE_ID"
```

Comparar traceId e spanId do log com Tempo, service.name=oficina-api, span SERVER,
rota/status; abrir o mesmo ID no Explore/Tempo. Confirmar ausência de tokens,
corpos e dados pessoais. Não considerar um trace sintético do receptor como aceite
da instrumentação da API. Registrar SHA da API, horário e ID sintético seguro.

## EC2 VIA SSM — rollback restrito

No mesmo worktree/SHA, com o caminho exato impresso na instalação:

```bash
cd "$OBS_WORKTREE"
export OBS_ATTEMPT='/var/lib/oficina-observability/attempt.XXXXXX'
sudo env KUBECTL="$OBS_WORKTREE/scripts/staging/observability/k3s-kubectl.sh" \
  EXPECTED_API_PODS=auto bash scripts/staging/observability/install.sh rollback "$OBS_ATTEMPT"
```

Restaura snapshots somente dos nomes envolvidos; remove objetos novos dessa
 tentativa, exceto PVCs. ConfigMaps antigos ficam disponíveis para restauração.
Não usar delete -k, delete namespace ou delete por label. Não altera API/HPA/Secrets.
Se rollback falhar, preservar o diretório e resolver a causa antes de repetir.

## Validação versus staging

Implementação e testes locais não comprovam instalação em staging. O smoke local
com Tempo limitado a 384MiB confirmou ingestão protobuf e consulta de trace
sintético em loopback. Nenhum deploy, aceite de trace real da API, mudança AWS ou
merge foi executado nesta entrega. CI e SHA finais constam na PR.
