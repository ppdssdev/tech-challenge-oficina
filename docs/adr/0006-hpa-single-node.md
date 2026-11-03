# ADR 0006 — HPA no nó único

**Status:** Manifest implementado e HPA observado; 2026-11-03. Registro consolidado a partir das fontes; não inventa data de
aprovação original nem transforma propostas em implantação.

## Contexto

Ajustar réplicas da API com métricas de recursos e demonstrar escalabilidade horizontal do processo.

## Decisão

autoscaling/v2 com min=1, max=3, CPU 70% e memória 75%; Deployment não fixa replicas. Requests CPU 100m/memória 384Mi por pod; limits CPU 1/memória 1Gi. Metrics Server fornece métricas.

## Consequências e reavaliação

HPA não cria nós, não aumenta teto físico e não garante HA. Memória JVM pode manter réplicas elevadas; amostra atual estava no limite de 3. Medir carga/latência e capacidade antes de ajustar thresholds. Requests/limits e observabilidade competem pelos recursos do mesmo nó.

## Evidências

[HPA](../../k8s/staging/hpa.yaml), [API](../../k8s/staging/api.yaml), [evidência](../fase3/operational-evidence.md).
