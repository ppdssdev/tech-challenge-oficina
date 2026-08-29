#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

kubectl apply -k "${PROJECT_ROOT}/k8s/local"
kubectl -n oficina rollout status deployment/oficina-postgres --timeout=180s
kubectl -n oficina rollout status deployment/oficina-mailpit --timeout=180s
kubectl -n oficina rollout status deployment/oficina-api --timeout=300s
