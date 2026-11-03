#!/usr/bin/env bash
# Offline only. Produces runtime YAML on stdout; never applies resources.
set -euo pipefail
image=${1:-}
if [[ ! "$image" =~ ^[a-zA-Z0-9][a-zA-Z0-9./:_-]*@sha256:[a-f0-9]{64}$ ]]; then
  echo 'Usage: bash scripts/staging/render.sh registry/repository@sha256:<64 lowercase hex>' >&2
  exit 1
fi
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cp "$root"/k8s/staging/*.yaml "$tmp/"
cat > "$tmp/image-digest.yaml" <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: oficina-api
spec:
  template:
    spec:
      containers:
        - name: api
          image: $image
EOF
kubectl kustomize "$tmp"
