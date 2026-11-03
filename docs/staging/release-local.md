# Release local de staging

## Correção da auditoria

O checkout original era main `7b47790`, com origin/main desatualizado. Em 2026-11-03,
`git fetch origin main` trouxe `045da59`; `git ls-tree origin/main
scripts/staging/render.sh` confirmou blob `c2d80eec9853dfe7c0683c52078f33ce022ac21e`.
A branch integrou main por merge, preservando `8a60d18` e arquivos locais.
O renderizador existe e é reutilizado sem alterações. O HPA também existe e o
Deployment não fixa mais replicas. O diagnóstico anterior era limitado por uma
referência remota desatualizada.

## Preparação offline e publicação

Requisitos offline: Bash, Git, Python 3 e kubectl com kustomize. Neste ambiente,
`/tmp/oficina-tools/kubectl` aponta para `/snap/kubectl/current/kubectl`, binário
já instalado. Executá-lo diretamente para renderização offline funcionou sem
mudar AppArmor, permissões, serviços ou configuração do sistema. Esse caminho é
local e temporário; em outra máquina use uma instalação de kubectl verificada.

```bash
export PATH="/tmp/oficina-tools:$PATH"
bash scripts/staging/release-local.sh --dry-run
```

Dry-run padrão não chama AWS nem Docker, não precisa de credenciais, permite
checkout sujo e usa digest fictício explicitamente identificado somente para
validar renderização. Não gera manifest aplicável persistente.

Publicação futura autorizada (não executada nesta tarefa):

```bash
bash scripts/staging/release-local.sh --execute
```

Exige checkout completamente limpo, ferramentas AWS CLI v2, Docker e tar, sessão
temporária no perfil oficina-staging com 30 minutos restantes, conta/região certas,
ECR IMMUTABLE sem exceções, tag ainda ausente e SSM Online. Exige terminal e a
palavra PUBLICAR antes de construir ou autenticar. Constrói somente arquivos do
commit via git archive para linux/amd64; confirmar compatibilidade do nó antes
de promover. Bases Docker e pacotes não são fixados por digest: rebuild não é
bit a bit reproduzível; a tag git-<SHA completo> nunca é sobrescrita. Uma tag já
existente bloqueia repetição, inclusive após falha posterior ao push.

Credenciais temporárias são verificadas em memória; token ECR passa por stdin e
configuração Docker fica em diretório restrito removido ao sair. Não usar debug
ou gravação de terminal. O script consulta o digest real após push e valida a
renderização. Não executa Kubernetes, migrações ou deploy. Se uma falha ocorrer
após push, a imagem pode permanecer no ECR; não a excluir automaticamente.

Para guardar o runtime após publicação, use o digest real impresso:

```bash
umask 077
IMAGE='797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api@sha256:<digest-real>'
bash scripts/staging/render.sh "$IMAGE" > /tmp/oficina-runtime-reviewed.yaml
```

## SSM: alternativa manual condicionada à revisão

[AWS documenta Run Command com AWS-RunShellScript](https://docs.aws.amazon.com/systems-manager/latest/userguide/walkthrough-cli.html).
Não foram consultadas permissões reais nem enviados comandos nesta tarefa.
SSM Online não comprova autorização SendCommand, sudo, RBAC, pull ECR ou métricas.
Deploy automático permanece bloqueado. Nenhum manifesto, credencial, Secret ou
kubeconfig será transportado por Run Command. Para uma promoção **somente de
imagem**, comandos abaixo operam sobre Deployment existente e preservam HPA,
Secrets e ConfigMaps. Não substituem aplicação de mudanças estruturais do runtime;
essas exigem transporte aprovado e revisão de diff próprios.

Operador deve validar conta e região antes de qualquer comando, registrar digest
anterior, conferir compatibilidade de schema e aprovar explicitamente a promoção.
Para cada operação abaixo, criar `/tmp/oficina-ssm.json` com comandos públicos e
revisá-lo antes de enviar. Não incluir tokens ou conteúdo de ConfigMaps/Secrets.

Preflight manual (JSON exato):

```bash
cat > /tmp/oficina-ssm.json <<'JSON'
{"commands":["set -eu","sudo k3s kubectl get namespace oficina-staging","sudo k3s kubectl -n oficina-staging get deployment oficina-api -o jsonpath='{.spec.template.spec.containers[?(@.name==\"api\")].image}'","sudo k3s kubectl -n oficina-staging get pods -l app=oficina-api -o wide","sudo k3s kubectl -n oficina-staging get hpa","sudo k3s kubectl -n oficina-staging get secret oficina-runtime oficina-application -o name","sudo k3s kubectl -n oficina-staging get configmap oficina-staging-db oficina-rds-ca oficina-customer-jwt -o name","sudo k3s kubectl get nodes -o wide"],"executionTimeout":["600"]}
JSON
```

Enviar **apenas após aprovação operacional**; função reutilizada para preflight,
promoção e rollback, sempre com JSON revisto:

```bash
ssm_reviewed() {
  test "$(aws --profile oficina-staging --region us-east-2 sts get-caller-identity --query Account --output text)" = 797781632267 || return 1
  local id
  id=$(aws --profile oficina-staging --region us-east-2 ssm send-command \
    --instance-ids i-0ab373b1ba5c14b3e --document-name AWS-RunShellScript \
    --parameters file:///tmp/oficina-ssm.json --timeout-seconds 600 \
    --query Command.CommandId --output text) || return 1
  aws --profile oficina-staging --region us-east-2 ssm wait command-executed \
    --command-id "$id" --instance-id i-0ab373b1ba5c14b3e || return 1
  aws --profile oficina-staging --region us-east-2 ssm get-command-invocation \
    --command-id "$id" --instance-id i-0ab373b1ba5c14b3e
}
ssm_reviewed
```

Confirmar arquitetura amd64, HPA único com métricas disponíveis, probes existentes,
referências externas intactas e autenticação ECR do nó previamente configurada.
Não transportar login ECR do operador para o nó. Ausência de qualquer evidência
bloqueia promoção. Um timeout do waiter não cancela a execução remota: consultar
CommandId antes de repetir ou iniciar rollback.

Após aprovação, gerar comando de promoção usando apenas digest validado:

```bash
export IMAGE='797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api@sha256:<digest-real>'
python3 - <<'PY'
import json, os, re
image = os.environ['IMAGE']
if not re.fullmatch(r'797781632267\.dkr\.ecr\.us-east-2\.amazonaws\.com/oficina-staging-api@sha256:[a-f0-9]{64}', image): raise SystemExit('Digest inválido')
k = 'sudo k3s kubectl -n oficina-staging '
commands = ['set -eu', k+'set image deployment/oficina-api api='+image,
 k+'rollout status deployment/oficina-api --timeout=300s',
 k+'get pods -l app=oficina-api -o wide', k+'get hpa',
 k+'''get pods -l app=oficina-api -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.containerStatuses[*].imageID}{" "}{.status.containerStatuses[*].ready}{"\\n"}{end}' ''']
with open('/tmp/oficina-ssm.json', 'w') as f:
 json.dump({'commands':commands, 'executionTimeout':['600']}, f)
PY
# Revisar arquivo e confirmar aprovação antes desta chamada:
ssm_reviewed
```

Exigir sucesso remoto, rollout completo, pods Ready, imageID esperado, probes
saudáveis, ausência de reinícios crescentes e HPA com métricas e condições
saudáveis. Validar liveness/readiness e autenticação pelo canal privado já
aprovado. Não abrir SSH, API Kubernetes ou portas públicas.

Rollback: registrar previamente IMAGE anterior por digest; confirmar sua
existência no ECR e compatibilidade com schema atual. Após aprovação, repetir
**o mesmo gerador Python e ssm_reviewed** com IMAGE anterior. Validar novamente
rollout, imagem efetiva, probes, pods e HPA. Não executar rollout undo sem conferir
revisão; não reverter schema, Secrets, ConfigMaps ou HPA. Nunca usar prune/delete,
Job de migração, Terraform apply/destroy/import ou alterar RDS/Lambda/IAM.

## Testes

```bash
python3 -m unittest discover -s tests/staging -v
python3 scripts/staging/test-render.py
bash -n scripts/staging/release-local.sh
/tmp/oficina-tools/shellcheck-v0.11.0/shellcheck scripts/staging/release-local.sh scripts/staging/render.sh
git diff --check
```

Mocks validam dry-run sem chamadas AWS/Docker, guards e bloqueio sem terminal.
Não validam push real, IAM ou cluster. CI existente permanece preservada; nenhum
workflow usa credenciais estáticas. PR deve aguardar revisão humana.
