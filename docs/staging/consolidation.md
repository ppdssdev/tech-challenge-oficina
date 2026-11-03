> **Registro histórico:** resultados e pendências abaixo pertencem à etapa original.
> Estado vigente em 2026-11-03: [consolidação auditada](../fase3/README.md).

# Consolidação de staging — Fase 3

Base auditada: main `7b4779052b2d3fde9e79042b96c41c047f4deb9b`.
A main tinha placeholder aplicável e não versionava o HPA de staging. Agora o
runtime exige renderização com digest e deixa a contagem de réplicas ao HPA.
Nenhuma alteração em Java, autenticação, TLS/RDS, probes, permissões, Secrets ou
ConfigMaps externos. Flyway continua separado; esta consolidação não requer migration.

Evidências informadas pelo operador: API 3/3 Ready, HPA operacional, CUSTOMER
RS256 emitido pela Lambda acessou OS própria com HTTP 200; ADMIN HMAC funcional.
Não foram repetidas contra AWS nesta PR. HPA: min=1, max=3, CPU=70%, memória=75%.
O nome versionado é oficina-api-hpa (convenção do repositório); confirmar o nome
existente antes de aplicar para não criar dois HPAs para o mesmo Deployment.

## JWKS

Arquivo público copiado sem alteração de
`fiap-customer-jwks-a2a5ff3ceaf0:docs/staging/customer-jwks.json`.
Após merge, a referência HTTPS estável para a chave externa `jwks-uri` do
ConfigMap `oficina-customer-jwt` é:

https://raw.githubusercontent.com/ppdssdev/tech-challenge-oficina/main/docs/staging/customer-jwks.json

Issuer: `https://synthetic-issuer.invalid`; audience: `oficina-staging-demo`;
kid: `oficina-staging-v1`. Não mudar o arquivo externo customer-jwt.yml nem o
isolamento ADMIN HMAC/CUSTOMER RS256. A PR disponibiliza a URL; a atualização do
ConfigMap externo é uma operação posterior do operador, após confirmar HTTP 200
anônimo e igualdade do JWKS. Nenhuma chave privada é necessária. A URL main é
estável mas seu conteúdo é mutável; mudanças de chave exigem revisão e rotação planejada.
Não remover a branch de origem antes da atualização externa e validação E2E.

## Validação local

Requisitos: bash, kubectl (somente kustomize local), Python 3 e PyYAML.

```sh
python3 scripts/staging/test-render.py
bash -n scripts/staging/render.sh
git diff --check
```

CI também valida os schemas Kubernetes com kubeconform. O digest fictício usado
no CI serve apenas para renderização e nunca pode ser usado em ambiente real.

## Preparação e validação pelo operador (não executadas nesta PR)

Selecionar explicitamente o contexto de staging. Guardar YAMLs abaixo em diretório
restrito; não versionar valores de ConfigMaps nem tokens. Confirmar que o digest
abaixo é o da imagem validada (não converter tag em digest por suposição).

```sh
CTX='<contexto-staging>'
kubectl --context "$CTX" -n oficina-staging get deployment oficina-api -o yaml > deployment-before.yaml
kubectl --context "$CTX" -n oficina-staging get hpa -o yaml > hpa-before.yaml
IMAGE=$(kubectl --context "$CTX" -n oficina-staging get deployment oficina-api -o jsonpath='{.spec.template.spec.containers[?(@.name=="api")].image}')
bash scripts/staging/render.sh "$IMAGE" > staging-reviewed.yaml
kubectl --context "$CTX" -n oficina-staging diff -f staging-reviewed.yaml
kubectl --context "$CTX" -n oficina-staging get deployment,pods,hpa
kubectl --context "$CTX" -n oficina-staging rollout status deployment/oficina-api --timeout=180s
curl --fail --silent --show-error https://raw.githubusercontent.com/ppdssdev/tech-challenge-oficina/main/docs/staging/customer-jwks.json -o /tmp/customer-jwks-main.json
cmp docs/staging/customer-jwks.json /tmp/customer-jwks-main.json
```

`diff` retorna 1 quando há diferenças. Revisar a imagem idêntica, referências
externas intactas e o HPA único. Nunca aplicar `api.yaml` isoladamente (não contém
imagem) nem o Job com placeholder. `kubectl apply -k k8s/staging` falha antes de
enviar recursos porque a patch de digest não está presente. Não criar essa patch
no repositório; render.sh trabalha em diretório temporário.

Após uma aplicação futura autorizada, validar readiness, métricas e HPA; repetir
os testes existentes CUSTOMER/OS própria e ADMIN com tokens fornecidos por meio
seguro, sem imprimi-los. A alteração externa de JWKS requer rollout controlado
conforme o runbook de deployment, pois não se presume hot reload.

## Rollback pelo operador

Esta PR não altera o cluster. Reverter o commit reverte apenas o Git. Caso uma
aplicação futura falhe, recuperar o digest anterior de deployment-before.yaml e
renderizar novamente com ele; revisar e reaplicar apenas o runtime em staging.
`kubectl --context "$CTX" -n oficina-staging rollout undo deployment/oficina-api`
é alternativa somente se a revisão anterior foi conferida. Isso não restaura
ConfigMaps externos nem HPA: restaurar seus valores anteriores pelo processo
operacional existente, incluindo a URI JWKS anterior se necessário, e validar
rollout/readiness e ambos os perfis de autenticação novamente. Não reexecutar nem
reverter migrations automaticamente; não usar terraform apply. Não atingir production.
