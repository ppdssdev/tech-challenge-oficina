#!/usr/bin/env bash
# Run manually on the K3s host through SSM; never invoked by release/CI.
set -Eeuo pipefail
set +x
umask 077
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)
KUBECTL=${KUBECTL:-kubectl}
STATE_DIR=${STATE_DIR:-/var/lib/oficina-observability}
EXPECTED_API_PODS=${EXPECTED_API_PODS:-auto}
NS=oficina-staging
OWNER='app.kubernetes.io/part-of=oficina-observability'
KINDS=deployments,services,configmaps,serviceaccounts,roles,rolebindings,persistentvolumeclaims
PF_PID=''
ATTEMPT=''
MUTATING=false
k() { "$KUBECTL" -n "$NS" "$@"; }
cleanup() { if [[ -n "$PF_PID" ]]; then kill "$PF_PID" 2>/dev/null || true; wait "$PF_PID" 2>/dev/null || true; PF_PID=''; fi; }
trap cleanup EXIT
usage() { echo "Uso: $0 install | rollback /var/lib/oficina-observability/attempt.XXXXXX" >&2; exit 2; }
[[ $# -ge 1 ]] || usage
MODE=$1
[[ "$MODE" == install && $# == 1 || "$MODE" == rollback && $# == 2 ]] || usage
[[ "$EXPECTED_API_PODS" == auto || "$EXPECTED_API_PODS" =~ ^[1-9][0-9]*$ ]] || usage
for tool in "$KUBECTL" python3 curl flock; do command -v "$tool" >/dev/null; done
mkdir -p "$STATE_DIR"
exec 9>"$STATE_DIR/lock"
flock -n 9 || { echo 'Outra operação de observabilidade está em execução.' >&2; exit 1; }

rollouts() {
    k rollout status deployment/oficina-observability-prometheus --timeout=300s &&
        k rollout status deployment/oficina-observability-grafana --timeout=300s
}
check_targets() {
    local temp_dir=$1
    cleanup
    # Port 0 lets kubectl select an available loopback port; no public listener.
    "$KUBECTL" -n "$NS" port-forward --address 127.0.0.1 service/oficina-observability-prometheus :9090 >"$temp_dir/port-forward.log" 2>&1 9>&- &
    PF_PID=$!
    local port='' i
    for ((i=0; i<30; i++)); do
        kill -0 "$PF_PID" 2>/dev/null || return 1
        port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) -> 9090$/\1/p' "$temp_dir/port-forward.log" | head -1)
        [[ -n "$port" ]] && break
        sleep 1
    done
    [[ -n "$port" ]] || return 1
    local sample='' previous=''
    for ((i=0; i<24; i++)); do
        kill -0 "$PF_PID" 2>/dev/null || return 1
        if k get deployment oficina-api -o json >"$temp_dir/deployment.json" &&
            k get pods -l app=oficina-api -o json >"$temp_dir/pods.json" &&
            curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:$port/api/v1/targets" >"$temp_dir/targets.json" &&
            k get pods -l app=oficina-api -o json >"$temp_dir/pods-after.json" &&
            k get deployment oficina-api -o json >"$temp_dir/deployment-after.json" &&
            sample=$(python3 "$ROOT/scripts/staging/observability/check-targets.py" \
                "$temp_dir/pods.json" "$temp_dir/targets.json" "$EXPECTED_API_PODS" \
                "$temp_dir/deployment.json" "$temp_dir/pods-after.json" "$temp_dir/deployment-after.json"); then
            if [[ "$sample" == "$previous" ]]; then
                printf '%s\n' "$sample"
                cleanup
                return 0
            fi
            previous=$sample
            # Require the same healthy pod identities across at least one scrape interval.
            sleep 15
        else
            previous=''
            sleep 5
        fi
    done
    cleanup
    return 1
}
rollback() {
    local attempt=$1
    [[ -f "$attempt/before.json" && -f "$attempt/attempted" ]] || { echo 'Snapshot inválido.' >&2; return 1; }
    cleanup
    k get "$KINDS" -l "$OWNER" -o json >"$attempt/current.json" || return 1
    # Preserve PVCs even after a failed first installation. No Secret is in this inventory.
    python3 - "$attempt" <<'PY' || return 1
import json, pathlib, sys
p=pathlib.Path(sys.argv[1]); before=json.loads((p/'before.json').read_text())
ids={(x['kind'],x['metadata']['name']) for x in before['items']}
current=json.loads((p/'current.json').read_text())
remove=[x for x in current['items'] if (x['kind'],x['metadata']['name']) not in ids and x['kind']!='PersistentVolumeClaim']
(p/'remove.json').write_text(json.dumps({'apiVersion':'v1','kind':'List','items':remove}))
print(f"Rollback: restaurar {len(ids)} objetos; remover {len(remove)} objetos novos; preservar PVC/Secret.")
PY
    # Explicit return checks: this function also runs inside an ERR handler.
    local before_count remove_count
    before_count=$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["items"]))' "$attempt/before.json") || return 1
    remove_count=$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["items"]))' "$attempt/remove.json") || return 1
    if [[ "$before_count" != 0 ]]; then
        k apply --server-side --field-manager=oficina-observability -f "$attempt/before.json" || return 1
    fi
    if [[ "$remove_count" != 0 ]]; then
        k delete -f "$attempt/remove.json" --ignore-not-found --wait=true --timeout=120s || return 1
    fi
    if python3 - "$attempt/before.json" <<'PY'
import json,sys
sys.exit(0 if any(x['kind']=='Deployment' for x in json.load(open(sys.argv[1]))['items']) else 1)
PY
    then
        rollouts && check_targets "$attempt" || return 1
    fi
    touch "$attempt/rolled-back" || return 1
    echo 'Rollback concluído; PVC e Secret preservados.'
}
on_error() {
    local status=${1:-$?}
    trap - ERR INT TERM
    if [[ "$MUTATING" == true ]]; then
        echo "Instalação falhou; iniciando rollback de $ATTEMPT" >&2
        if ! rollback "$ATTEMPT"; then
            echo "ROLLBACK FALHOU. Preserve $ATTEMPT e execute rollback manual após corrigir a causa." >&2
        fi
    fi
    exit "$status"
}
if [[ "$MODE" == rollback ]]; then
    rollback "$2"
    exit 0
fi
trap on_error ERR
trap 'on_error 130' INT
trap 'on_error 143' TERM
# Preflight never changes API/namespace/Secret/storage class.
k get namespace "$NS" >/dev/null
k get secret oficina-observability-grafana-admin -o name >/dev/null
"$KUBECTL" get storageclass local-path >/dev/null
ATTEMPT=$(mktemp -d "$STATE_DIR/attempt.XXXXXX")
"$KUBECTL" kustomize "$ROOT/k8s/staging/observability" >"$ATTEMPT/desired.yaml"
k get -f "$ATTEMPT/desired.yaml" --ignore-not-found -o json >"$ATTEMPT/collisions.json"
python3 - "$ATTEMPT/collisions.json" <<'PY2'
import json,sys
obj=json.load(open(sys.argv[1]))
items=obj.get('items',[]) if obj.get('kind')=='List' else [obj]
for item in items:
    if item['metadata'].get('labels',{}).get('app.kubernetes.io/part-of')!='oficina-observability':
        sys.exit('Nome já utilizado por recurso não gerenciado: '+item['metadata']['name'])
PY2
k get "$KINDS" -l "$OWNER" -o json >"$ATTEMPT/before.raw.json"
python3 - "$ATTEMPT" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1]); data=json.loads((p/'before.raw.json').read_text())
for item in data['items']:
    item.pop('status',None)
    for key in ['uid','resourceVersion','generation','creationTimestamp','managedFields']:
        item['metadata'].pop(key,None)
(p/'before.json').write_text(json.dumps(data))
PY
# Server validation occurs only when an operator executes install on staging.
k apply --server-side --field-manager=oficina-observability --dry-run=server -f "$ATTEMPT/desired.yaml" >/dev/null
printf 'Snapshot para rollback: %s\n' "$ATTEMPT"
MUTATING=true
touch "$ATTEMPT/attempted"
k apply --server-side --field-manager=oficina-observability -f "$ATTEMPT/desired.yaml"
rollouts
check_targets "$ATTEMPT"
MUTATING=false
touch "$ATTEMPT/succeeded"
echo 'Observabilidade instalada: rollouts concluídos e targets individuais UP.'
