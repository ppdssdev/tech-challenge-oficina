#!/usr/bin/env bash
set -euo pipefail

cleanup() {
  kill "${api_pid:-}" "${mailpit_pid:-}" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

kubectl -n oficina port-forward service/oficina-api 8080:8080 &
api_pid=$!
kubectl -n oficina port-forward service/oficina-mailpit 8025:8025 &
mailpit_pid=$!

echo "API:     http://localhost:8080"
echo "Mailpit: http://localhost:8025"
echo "Pressione Ctrl+C para encerrar os dois port-forwards."

wait -n "${api_pid}" "${mailpit_pid}"
