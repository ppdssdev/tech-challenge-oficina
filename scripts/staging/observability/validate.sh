#!/usr/bin/env bash
# Offline relative to Kubernetes/AWS. Docker may download images/schema files.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)
cd "$ROOT"
KUBECTL=${KUBECTL:-kubectl}
export KUBECTL
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT
bash -n scripts/staging/observability/install.sh
shellcheck scripts/staging/observability/*.sh
"$KUBECTL" kustomize k8s/staging/observability >"$TEMP_DIR/rendered.yaml"
python3 scripts/staging/observability/test-observability.py
python3 - "$TEMP_DIR/rules.yaml" <<'PY'
import json,sys,yaml
from pathlib import Path
path=Path('k8s/staging/observability/config')
dashboard=json.loads((path/'dashboard.json').read_text())
rules=[{'record':f'check_panel_{panel["id"]}_{i}',
        'expr':target['expr'].replace('$__rate_interval','1m')}
       for panel in dashboard['panels'] for i,target in enumerate(panel['targets'])]
Path(sys.argv[1]).write_text(yaml.safe_dump({'groups':[{'name':'dashboard-syntax','rules':rules}]}))
PY
PROMETHEUS=$(python3 - <<'PY'
import yaml
with open('k8s/staging/observability/prometheus.yaml') as f:
    print(next(yaml.safe_load_all(f))['spec']['template']['spec']['containers'][0]['image'])
PY
)
docker run --rm --network none --entrypoint /bin/promtool \
    -v "$ROOT/k8s/staging/observability/config/prometheus.yml:/config/prometheus.yml:ro" \
    "$PROMETHEUS" check config --syntax-only /config/prometheus.yml
# mktemp defaults to 0700; mount only the readable file for non-root promtool.
chmod 644 "$TEMP_DIR/rules.yaml"
docker run --rm --network none --entrypoint /bin/promtool \
    -v "$TEMP_DIR/rules.yaml:/rules.yaml:ro" "$PROMETHEUS" check rules /rules.yaml
docker run --rm -i ghcr.io/yannh/kubeconform@sha256:0925177fb05b44ce18574076141b5c3d83235e1904d3f952182ac99ddc45762c \
    -strict -summary -kubernetes-version 1.34.0 <"$TEMP_DIR/rendered.yaml"
