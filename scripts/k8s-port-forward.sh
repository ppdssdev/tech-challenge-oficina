#!/usr/bin/env bash
set -euo pipefail

cleanup() {
  kill \
    "${api_pid:-}" \
    "${mailpit_pid:-}" \
    "${prometheus_pid:-}" \
    "${grafana_pid:-}" \
    2>/dev/null || true
}

trap cleanup EXIT INT TERM

kubectl -n oficina port-forward service/oficina-api 8080:8080 &
api_pid=$!
kubectl -n oficina port-forward service/oficina-mailpit 8025:8025 &
mailpit_pid=$!
kubectl -n oficina port-forward service/oficina-prometheus 9090:9090 &
prometheus_pid=$!
kubectl -n oficina port-forward service/oficina-grafana 3000:3000 &
grafana_pid=$!

echo "API:        http://localhost:8080"
echo "Mailpit:    http://localhost:8025"
echo "Prometheus: http://localhost:9090"
echo "Grafana:    http://localhost:3000"
echo "Pressione Ctrl+C para encerrar todos os port-forwards."

wait -n "${api_pid}" "${mailpit_pid}" "${prometheus_pid}" "${grafana_pid}"
