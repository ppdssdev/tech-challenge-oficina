#!/usr/bin/env bash
set -euo pipefail

readonly CLUSTER_NAME="oficina-local"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if kind get clusters 2>/dev/null | grep -Fxq "${CLUSTER_NAME}"; then
  echo "Cluster Kind '${CLUSTER_NAME}' já existe."
  exit 0
fi

kind create cluster \
  --name "${CLUSTER_NAME}" \
  --config "${PROJECT_ROOT}/k8s/kind/oficina-kind.yaml"
