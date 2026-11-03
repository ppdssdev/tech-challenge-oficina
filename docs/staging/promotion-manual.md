# Promoção manual da API e rollback por SSM

Este procedimento estende o Run Command de `release-local.md`. Não é CD pelo
GitHub, não publica imagem e não instala infraestrutura. Executar somente pelo
operador, após revisão/aprovação operacional. Nesta entrega nenhum comando AWS
ou Kubernetes remoto foi executado.

## Sequência e ambientes

1. Merge humano da PR #15 (tracing), depois #16 (métricas), depois desta PR de
   configuração/procedimento. Conferir checks do SHA exato antes de cada merge.
   Após integrar #15, buscar a nova main e revalidar #16 antes de integrá-la:

   ```bash
   git fetch origin
   git switch feat/fase3-business-metrics
   git merge --no-edit origin/main
   CUSTOMER_TEST_PYTHON=/caminho/venv/bin/python mvn -B clean verify -Dlambda.repo=/caminho/checkout-lambda -Dlambda.python=/caminho/venv/bin/python
   docker build --platform linux/amd64 -t oficina-api:pr16-revalidated .
   git push origin HEAD:feat/fase3-business-metrics
   ```

   Usar worktree isolado da branch #16. Aguardar CI verde do novo SHA e revisar
   diff contra a nova main; somente então realizar o merge humano da #16.
   Se a main mudar novamente, repetir a revalidação. Não usar force push.
2. Em checkout limpo da `main` resultante, validar e publicar com o release local.
3. Sessão 1 confirma Tempo pronto; operador captura estado anterior e promove.
4. Aceitar somente depois de rollout, probes, correlação JSON/Tempo e métricas.

| Ambiente | Release | Promoção |
|---|---|---|
| Local isolado | `mvn -B clean verify`; `docker build -t tech-challenge-oficina:local .` | Procedimento existente: `bash scripts/kind-load-image.sh`, `bash scripts/k8s-deploy-local.sh`, conferindo antes o contexto `kind-oficina-local`. Esses scripts alteram o cluster local; não foram executados aqui. |
| Staging | `bash scripts/staging/release-local.sh --dry-run`, depois `--execute` pelo operador | SSM abaixo, exclusivamente por digest do ECR; publicação ECR não promove. |
| Produção | Não definido neste repositório | Não reutilizar perfil, conta, instância ou comandos de staging. |

`release-local.sh` já valida conta/perfil/região, sessão renovável, ECR imutável,
digest do push versus ECR e SSM Online. O build usa conteúdo rastreado do commit.
Não sobrescrever tag existente: recuperar o digest publicado e conferir evidências
daquele build. O script não aplica manifests nem muda configuração da API.

## Gate da sessão 1

Registrar responsável, horário UTC, referência da entrega e evidências de:

- Tempo Ready, Service `oficina-tempo` no namespace `oficina-staging`, receiver
  privado OTLP HTTP/protobuf em 4318, `/v1/traces`, acessível a partir da API.
- URL privada de consulta de traces confirmada pela sessão 1 (porta de consulta
  não é a porta OTLP). DNS/NetworkPolicy/armazenamento pertencem à sessão 1.
- Janela da demonstração com início/fim, sampling 1.0 e posterior restauração
  da configuração capturada. Serializar as operações da API entre sessões.

Os valores aprovados estão em `scripts/staging/tracing-demo.env`. Esse arquivo
não é importado pelo overlay, release ou aplicação. Merge desta PR não ativa
exportação. Sem confirmação da sessão 1, parar antes da promoção.

## Sessão local do operador e transporte existente

Usar Bash, AWS CLI v2, Python 3; no nó, `sudo k3s kubectl`, Python 3 e curl já
disponíveis. Não instalar ferramentas, criar permissões ou transportar credenciais.
Executar os blocos na mesma sessão, na raiz de um checkout revisado.

```bash
set -euo pipefail
set +x
umask 077
export EVIDENCE_DIR
EVIDENCE_DIR=$(mktemp -d /tmp/oficina-promotion.XXXXXXXX)
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_SECURITY_TOKEN AWS_ENDPOINT_URL
export AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true AWS_PAGER='' AWS_CLI_AUTO_PROMPT=off
aws_local() { aws --profile oficina-staging --region us-east-2 "$@"; }
preflight_aws() {
  test "${AWS_REGION:-us-east-2}" = us-east-2 || return 1
  test "${AWS_DEFAULT_REGION:-us-east-2}" = us-east-2 || return 1
  test "$(aws_local configure get region)" = us-east-2 || return 1
  test "$(aws_local sts get-caller-identity --query Account --output text)" = 797781632267 || return 1
  test "$(aws_local ssm describe-instance-information \
    --filters Key=InstanceIds,Values=i-0ab373b1ba5c14b3e \
    --query 'InstanceInformationList[0].[InstanceId,PingStatus]' --output text)" = $'i-0ab373b1ba5c14b3e\tOnline' || return 1
}
preflight_aws
git rev-parse HEAD > "$EVIDENCE_DIR/source-sha.txt"
date -u +%FT%TZ > "$EVIDENCE_DIR/started-at.txt"

# Reusa AWS-RunShellScript; cada arquivo contém apenas comandos públicos revistos.
# Um label novo por envio; não reenviar uma operação de resultado desconhecido.
ssm_reviewed() {
  local label=$1 script=$2 id status
  preflight_aws || return 1
  test ! -e "$EVIDENCE_DIR/$label.command-id" || return 1
  python3 - "$script" "$EVIDENCE_DIR/$label.parameters.json" <<'PY'
import json, pathlib, sys
script = pathlib.Path(sys.argv[1]).read_text()
assert '\nOFICINA_REVIEWED\n' not in script
payload = "bash -se <<'OFICINA_REVIEWED'\n" + script + '\nOFICINA_REVIEWED\n'
pathlib.Path(sys.argv[2]).write_text(json.dumps({'commands': [payload], 'executionTimeout': ['900']}))
PY
  id=$(aws_local ssm send-command --instance-ids i-0ab373b1ba5c14b3e \
    --document-name AWS-RunShellScript --timeout-seconds 600 \
    --parameters "file://$EVIDENCE_DIR/$label.parameters.json" \
    --query Command.CommandId --output text)
  printf '%s\n' "$id" > "$EVIDENCE_DIR/$label.command-id"
  # O waiter dura menos que rollout+validação. Repetir a espera, nunca o envio.
  for attempt in {1..10}; do
    aws_local ssm wait command-executed --command-id "$id" \
      --instance-id i-0ab373b1ba5c14b3e || true
    aws_local ssm get-command-invocation --command-id "$id" \
      --instance-id i-0ab373b1ba5c14b3e > "$EVIDENCE_DIR/$label.invocation.json"
    status=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["Status"])' "$EVIDENCE_DIR/$label.invocation.json")
    case "$status" in
      Success) python3 - "$EVIDENCE_DIR/$label.invocation.json" <<'PY'
import json, sys
r=json.load(open(sys.argv[1]))
assert r['ResponseCode'] == 0 and r['InstanceId'] == 'i-0ab373b1ba5c14b3e'
PY
        return 0 ;;
      Pending|InProgress|Delayed) continue ;;
      *) echo "SSM terminou sem sucesso: $status; preservar evidências" >&2; return 1 ;;
    esac
  done
  echo "SSM indeterminado: consultar $id antes de repetir ou reverter" >&2
  return 1
}
```

O [waiter AWS](https://docs.aws.amazon.com/cli/latest/reference/ssm/wait/command-executed.html)
consulta a cada 5 s e encerra após 20 tentativas. Timeout local não cancela comando
remoto. Se uma chamada de consulta falhar, recuperar pelo CommandId registrado;
nunca inferir que não houve mutação. Não usar `set -x` nem registrar tokens.

## Capturar digest e configuração anteriores

O snapshot exporta apenas imagem, UID, três variáveis de tracing e probes. Não
exporta Secrets, kubeconfig ou outras variáveis. Referências `valueFrom` dessas
três variáveis são preservadas como referências. Estado anterior sem digest
imutável bloqueia: identificar e revisar o digest efetivo antes de prosseguir.

```bash
cat > "$EVIDENCE_DIR/snapshot.sh" <<'SH'
set -euo pipefail
python3 - <<'PY'
import json, re, subprocess
k=['sudo','k3s','kubectl','-n','oficina-staging']
d=json.loads(subprocess.check_output(k+['get','deployment','oficina-api','-o','json']))
c=next(x for x in d['spec']['template']['spec']['containers'] if x['name']=='api')
assert re.fullmatch(r'797781632267\.dkr\.ecr\.us-east-2\.amazonaws\.com/oficina-staging-api@sha256:[a-f0-9]{64}', c['image']), 'Imagem anterior deve estar por digest'
names={'MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED','MANAGEMENT_OTLP_TRACING_ENDPOINT','MANAGEMENT_TRACING_SAMPLING_PROBABILITY'}
assert c['readinessProbe']['httpGet']['path']=='/actuator/health/readiness'
assert c['livenessProbe']['httpGet']['path']=='/actuator/health/liveness'
print(json.dumps({'uid':d['metadata']['uid'], 'image':c['image'],
 'env':[x for x in c.get('env',[]) if x['name'] in names],
 'readinessProbe':c['readinessProbe'], 'livenessProbe':c['livenessProbe']}))
PY
SH
# Revisar snapshot.sh, então executar manualmente:
ssm_reviewed before "$EVIDENCE_DIR/snapshot.sh"
python3 - <<'PY'
import json, os, pathlib
p=pathlib.Path(os.environ['EVIDENCE_DIR'])
r=json.loads((p/'before.invocation.json').read_text())
before=json.loads(r['StandardOutputContent'])
(p/'before.json').write_text(json.dumps(before, indent=2))
PY

export IMAGE='797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api@sha256:<digest-real-publicado>'
python3 - <<'PY'
import json, os, pathlib, re
p=pathlib.Path(os.environ['EVIDENCE_DIR'])
before=json.loads((p/'before.json').read_text())
image=os.environ['IMAGE']
assert re.fullmatch(r'797781632267\.dkr\.ecr\.us-east-2\.amazonaws\.com/oficina-staging-api@sha256:[a-f0-9]{64}',image)
env=[]
for line in pathlib.Path('scripts/staging/tracing-demo.env').read_text().splitlines():
 if line and not line.startswith('#'):
  name,value=line.split('=',1); env.append({'name':name,'value':value})
(p/'desired.json').write_text(json.dumps({'uid':before['uid'],'image':image,'env':env}))
PY
for digest in "${IMAGE##*@}" "$(python3 -c 'import json,os; print(json.load(open(os.environ["EVIDENCE_DIR"]+"/before.json"))["image"].split("@")[1])')"; do
  test "$(aws_local ecr describe-images --repository-name oficina-staging-api \
    --image-ids "imageDigest=$digest" --query 'imageDetails[0].imageDigest' --output text)" = "$digest"
done
```

Conferir também arquitetura amd64 do nó, RBAC/sudo, pull ECR do nó, HPA saudável,
disponibilidade da API e compatibilidade da imagem anterior com o schema atual
pelos canais existentes. Este procedimento não executa migrações. Se qualquer
precondição falhar, interromper; não alterar IAM, banco ou autenticação para passar.

## Gerar promoção, restauração de sampling e rollback offline

Este bloco apenas gera três arquivos revisáveis. A mesma operação limitada altera
imagem e as três variáveis em um único patch; preserva outras configurações.
A API Kubernetes recebe um [JSON Patch](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/update-api-object-kubectl-patch/)
com teste de resourceVersion para rejeitar concorrência. Estado divergente exige
nova captura/revisão, nunca sobrescrita cega.

```bash
python3 - <<'PY'
import json, os, pathlib
p=pathlib.Path(os.environ['EVIDENCE_DIR'])
before=json.loads((p/'before.json').read_text())
desired=json.loads((p/'desired.json').read_text())
steady={**desired,'env':before['env']}
code=r'''
import json, subprocess
k=['sudo','k3s','kubectl','-n','oficina-staging']
d=json.loads(subprocess.check_output(k+['get','deployment','oficina-api','-o','json']))
containers=d['spec']['template']['spec']['containers']
i=next(i for i,c in enumerate(containers) if c['name']=='api'); c=containers[i]
names={'MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED','MANAGEMENT_OTLP_TRACING_ENDPOINT','MANAGEMENT_TRACING_SAMPLING_PROBABILITY'}
current={x['name']:x for x in c.get('env',[]) if x['name'] in names}
expected={x['name']:x for x in source['env']}
assert d['metadata']['uid']==source['uid']==target['uid'], 'Deployment recriado'
assert c['image']==source['image'] and current==expected, 'Estado mudou; revisar antes de continuar'
env=[x for x in c.get('env',[]) if x['name'] not in names]+target['env']
base=f'/spec/template/spec/containers/{i}'
patch=[{'op':'test','path':'/metadata/resourceVersion','value':d['metadata']['resourceVersion']},
 {'op':'replace','path':base+'/image','value':target['image']},
 {'op':'add','path':base+'/env','value':env}]
# Conteúdo completo do env permanece em memória no nó, nunca em argv ou evidências.
subprocess.run(k+['patch','deployment','oficina-api','--type=json','--patch-file=/dev/stdin'],
 input=json.dumps(patch).encode(), check=True, stdout=subprocess.DEVNULL)
subprocess.run(k+['rollout','status','deployment/oficina-api','--timeout=300s'], check=True)
'''
for name,source,target in [('promote',before,desired),('end-demo',desired,steady),
                           ('rollback',desired,before)]:
 script="set -euo pipefail\npython3 - <<'PY_REMOTE'\n"
 script+='source=json.loads('+repr(json.dumps(source))+')\n'
 script+='target=json.loads('+repr(json.dumps(target))+')\n'
 script=script.replace("python3 - <<'PY_REMOTE'\n", "python3 - <<'PY_REMOTE'\nimport json\n")
 (p/(name+'.sh')).write_text(script+code+'\nPY_REMOTE\n')
PY
```

## Validar rollout, imagem efetiva e probes

Gerar verificação reutilizável. O digest de cada `imageID` deve coincidir com o
digest aprovado; se o ECR contiver um índice multiplataforma, revisar e registrar
o mapeamento índice → manifest linux/amd64 antes de adaptar essa comparação.
Não aceitar tag ou coincidência parcial como evidência.

```bash
cat > "$EVIDENCE_DIR/verify.py" <<'PY'
import json, subprocess, urllib.request
k=['sudo','k3s','kubectl','-n','oficina-staging']
d=json.loads(subprocess.check_output(k+['get','deployment','oficina-api','-o','json']))
assert d['status']['observedGeneration']==d['metadata']['generation']
replicas=d['spec'].get('replicas',1)
assert replicas>0 and all(d['status'].get(x,0)==replicas for x in ['replicas','updatedReplicas','readyReplicas','availableReplicas'])
c=next(c for c in d['spec']['template']['spec']['containers'] if c['name']=='api')
names={x['name'] for x in expected['env']} | {'MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED','MANAGEMENT_OTLP_TRACING_ENDPOINT','MANAGEMENT_TRACING_SAMPLING_PROBABILITY'}
assert d['metadata']['uid']==expected['uid'] and c['image']==expected['image']
assert {x['name']:x for x in c.get('env',[]) if x['name'] in names}=={x['name']:x for x in expected['env']}
pods=json.loads(subprocess.check_output(k+['get','pods','-l','app=oficina-api','-o','json']))['items']
pods=[p for p in pods if not p['metadata'].get('deletionTimestamp')]
assert len(pods)==replicas
for p in pods:
 status=next(x for x in p['status']['containerStatuses'] if x['name']=='api')
 assert status['ready'] and 'running' in status['state']
 actual=status['imageID'].split('@')[-1].removeprefix('containerd://').removeprefix('docker://')
 assert actual==expected['image'].split('@')[-1], status['imageID']
 for probe,path in [('readinessProbe','readiness'),('livenessProbe','liveness')]:
  assert c[probe]['httpGet']['path']=='/actuator/health/'+path
  with urllib.request.urlopen('http://'+p['status']['podIP']+':8080/actuator/health/'+path,timeout=5) as r:
   assert r.status==200 and json.load(r)['status']=='UP'
 print(json.dumps({'pod':p['metadata']['name'],'imageID':status['imageID'],
  'ready':status['ready'],'restarts':status['restartCount'],'probes':'UP'}))
subprocess.run(k+['get','hpa'],check=True)
PY
python3 - <<'PY'
import json, os, pathlib
p=pathlib.Path(os.environ['EVIDENCE_DIR'])
before=json.loads((p/'before.json').read_text()); desired=json.loads((p/'desired.json').read_text())
steady={**desired,'env':before['env']}
for label,expected in [('promote',desired),('rollback',before),('end-demo',steady)]:
 with (p/(label+'.sh')).open('a') as f:
  f.write("python3 - <<'PY_VERIFY'\nimport json\nexpected=json.loads("+repr(json.dumps(expected))+')\n')
  f.write((p/'verify.py').read_text()+'\nPY_VERIFY\n')
PY

# Somente após gate Tempo, revisão de promote.sh e aprovação operacional:
ssm_reviewed promote "$EVIDENCE_DIR/promote.sh"
# Repetir a verificação após um intervalo de probes e comparar restartCount.
# Para isso copiar somente o bloco PY_VERIFY para verify-again.sh e usar label novo.
```

## Aceite runtime e encerramento da janela

No canal privado já aprovado, realizar uma requisição real de leitura com um
`traceparent` novo e amostrado (não reutilizar o trace de testes locais). Exemplo
na sessão privada do operador; `API_URL` e `TEMPO_QUERY_URL` devem apontar para
os forwards/endereços privados confirmados, nunca para endpoint público novo:

```bash
TRACE_ID=$(python3 -c 'import secrets; print(secrets.token_hex(16))')
PARENT_ID=$(python3 -c 'import secrets; print(secrets.token_hex(8))')
curl --fail --silent --show-error --max-time 10 \
  -H "traceparent: 00-$TRACE_ID-$PARENT_ID-01" \
  "$API_URL/actuator/health" > "$EVIDENCE_DIR/request-health.json"
printf '%s\n' "$TRACE_ID" > "$EVIDENCE_DIR/trace-id.txt"
# Aguardar exportação assíncrona; consultar novamente em caso de 404 inicial.
curl --fail --silent --show-error --max-time 10 -H 'Accept: application/json' \
  "$TEMPO_QUERY_URL/api/traces/$TRACE_ID" > "$EVIDENCE_DIR/tempo-trace.json"
curl --fail --silent --show-error --max-time 10 \
  "$API_URL/actuator/prometheus" > "$EVIDENCE_DIR/business-metrics.prom"
```

Pelo SSM existente, capturar somente a linha JSON `HTTP request completed` desse
trace em `kubectl logs -n oficina-staging -l app=oficina-api -c api --since=5m
--tail=500`; filtrar no nó pelo traceId, sem publicar logs completos. Guardar
`request-log.json`. Comparar traceId e spanId do servidor com o resultado Tempo,
`service.name=oficina-api`, rota `/actuator/health` e status 200. A resposta Tempo
pode representar IDs em base64: decodificar antes de comparar com hexadecimal.
Um 200 do endpoint de consulta sem o span correspondente não satisfaz o aceite.

Confirmar em `/actuator/prometheus` e no Prometheus privado: série
`oficina_work_orders_created_today` finita, métricas
`oficina_work_order_status_completed_duration_seconds` e
`oficina_work_order_status_completed_intervals`, counter
`oficina_notification_processing_failures_total` e snapshot timestamp finito
com idade <=120 s. Zero é válido; NaN/ausência não é. Esperar coleta de até 60 s.
Conferir targets individuais UP; gauges do mesmo banco usam `max`, nunca soma
de réplicas. Não criar OS nem simular falhas contra o banco staging nesta tarefa.
Consultar `docs/fase3/business-metrics-contract.md` para catálogo e PromQL.

Registrar evidências privadas, hashes, horários, SHA, digest, CommandIds, resposta
SSM, pods/probes, linha JSON, trace Tempo e métricas; compartilhar só amostras
sanitizadas. Guardar `before.json` até o fim da janela de rollback. **Runtime
permanece pendente até existir esse conjunto de evidências.**

Ao encerrar a demonstração, mantendo a imagem nova e restaurando as três variáveis
anteriores, revisar e executar `ssm_reviewed end-demo "$EVIDENCE_DIR/end-demo.sh"`.
Se antes eram ausentes, voltam a ser ausentes (defaults da imagem: exportação
desabilitada, sampling 0.1). Não deixar sampling 1.0 sem janela aprovada.

## Rollback explícito

Se a promoção falhar, primeiro consultar o CommandId até estado terminal. Obter
novo snapshot com label distinto para identificar se o patch ocorreu. Se imagem
e env já são os anteriores, não reaplicar. Se são os desejados, o rollback gerado
é aplicável mesmo com rollout incompleto: `ssm_reviewed rollback
"$EVIDENCE_DIR/rollback.sh"`, após revisão/aprovação. Ele restaura **digest e
configuração** anteriores, aguarda rollout e executa a mesma validação de probes
e imagem efetiva. Ausência anterior de variável é restaurada como ausência.

Se houve `end-demo` ou intervenção concorrente, capturar o estado atual, revisar
e regenerar o mesmo bloco com `source=estado_atual` e `target=before`; não mudar
o snapshot original. Confirmar novamente digest anterior no ECR e compatibilidade
de schema. Rollback não restaura banco, Secrets, ConfigMaps, HPA ou autenticação.
Não usar `rollout undo` sem identidade da imagem/configuração, nem remover imagens
ECR ou recursos. Falha de rollback exige preservar evidências e diagnóstico humano.
