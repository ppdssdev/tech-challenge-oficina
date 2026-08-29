#!/usr/bin/env bash
set -euo pipefail

readonly CLUSTER_NAME="oficina-local"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if kind get clusters 2>/dev/null | grep -Fxq "${CLUSTER_NAME}"; then
  kubectl --context "kind-${CLUSTER_NAME}" delete \
    -k "${PROJECT_ROOT}/k8s/local" \
    --ignore-not-found || true
  kind delete cluster --name "${CLUSTER_NAME}"
else
  echo "Cluster Kind '${CLUSTER_NAME}' não existe."
fi
