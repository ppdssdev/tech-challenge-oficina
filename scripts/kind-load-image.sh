#!/usr/bin/env bash
set -euo pipefail

readonly CLUSTER_NAME="oficina-local"
readonly IMAGE_NAME="tech-challenge-oficina:local"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

docker build -t "${IMAGE_NAME}" "${PROJECT_ROOT}"
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"
