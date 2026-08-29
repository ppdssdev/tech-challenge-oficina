#!/usr/bin/env bash
set -euo pipefail

if ! command -v kubectl >/dev/null 2>&1; then
  echo "Erro: kubectl não está disponível no PATH." >&2
  exit 1
fi

readonly METRICS_SERVER_MANIFEST="https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml"

echo "Instalando Metrics Server no namespace kube-system..."
kubectl apply -f "${METRICS_SERVER_MANIFEST}"

if ! kubectl -n kube-system get deployment metrics-server \
  -o jsonpath='{.spec.template.spec.containers[0].args}' | grep -q -- '--kubelet-insecure-tls'; then
  echo "Configurando Metrics Server para o kubelet do Kind..."
  kubectl -n kube-system patch deployment metrics-server \
    --type=json \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
fi

echo "Aguardando o rollout do Metrics Server..."
kubectl -n kube-system rollout status deployment/metrics-server --timeout=180s
echo "Metrics Server pronto. As métricas podem levar alguns instantes para aparecer no kubectl top e no HPA."
