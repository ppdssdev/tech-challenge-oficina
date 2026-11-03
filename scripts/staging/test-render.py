"""Offline regression checks; requires kubectl and PyYAML."""
import json
import subprocess
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[2]
def run(*args):
    return subprocess.run(args, cwd=root, text=True, capture_output=True)

for image in ('', 'oficina-api:staging-review-required', 'repo:latest', 'repo@sha256:1234'):
    result = run('bash', 'scripts/staging/render.sh', image)
    assert result.returncode != 0 and not result.stdout, image
result = run('kubectl', 'kustomize', 'k8s/staging')
assert result.returncode != 0 and 'image-digest.yaml' in result.stderr
image = 'example.invalid/oficina@sha256:' + 'a' * 64
result = run('bash', 'scripts/staging/render.sh', image)
assert result.returncode == 0, result.stderr
docs = list(yaml.safe_load_all(result.stdout))
assert {d['kind'] for d in docs} == {'Namespace', 'Deployment', 'Service', 'HorizontalPodAutoscaler'}
deployment = next(d for d in docs if d['kind'] == 'Deployment')
assert 'replicas' not in deployment['spec']
assert deployment['spec']['template']['spec']['containers'][0]['image'] == image
assert all(d['metadata']['namespace'] == 'oficina-staging' for d in docs if d['kind'] != 'Namespace')
hpa = next(d['spec'] for d in docs if d['kind'] == 'HorizontalPodAutoscaler')
assert (hpa['minReplicas'], hpa['maxReplicas']) == (1, 3)
assert hpa['scaleTargetRef']['name'] == 'oficina-api'
assert {m['resource']['name']: m['resource']['target']['averageUtilization'] for m in hpa['metrics']} == {'cpu': 70, 'memory': 75}
keys = json.loads((root / 'docs/staging/customer-jwks.json').read_text())['keys']
assert len(keys) == 1
key = keys[0]
assert set(key) == {'kty', 'use', 'alg', 'kid', 'n', 'e'}
assert (key['kty'], key['use'], key['alg'], key['kid']) == ('RSA', 'sig', 'RS256', 'oficina-staging-v1')
assert key['n'] and key['e'] == 'AQAB'
print('PASS: digest required, direct overlay blocked, HPA, namespace, migration isolation, public JWKS')
