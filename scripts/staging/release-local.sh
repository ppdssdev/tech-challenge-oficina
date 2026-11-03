#!/usr/bin/env bash
# Deliberately no remote mutation until a reviewed deployment transport exists.
set +x
set -euo pipefail
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }
mode=${1:---dry-run}
[[ $# -le 1 && ( $mode == --dry-run || $mode == --execute ) ]] || fail 'Uso: release-local.sh [--dry-run|--execute]'
for tool in git aws docker python3; do
  command -v "$tool" >/dev/null || fail "Ferramenta ausente: $tool"
done
root=$(git rev-parse --show-toplevel)
cd "$root"
commit=$(git rev-parse --verify HEAD)
[[ $commit =~ ^[0-9a-f]{40}$ ]] || fail 'Commit inválido'
[[ -z $(git status --porcelain --untracked-files=all) ]] || fail 'Checkout deve estar limpo, incluindo arquivos não rastreados'
[[ ${AWS_REGION:-us-east-2} == us-east-2 && ${AWS_DEFAULT_REGION:-us-east-2} == us-east-2 ]] || fail 'Região ambiente divergente'
# Ignore ambient credentials and endpoints; resolve only the named local profile.
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_SECURITY_TOKEN AWS_ENDPOINT_URL
export AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true AWS_PAGER='' AWS_CLI_AUTO_PROMPT=off
aws_local() { aws --profile oficina-staging --region us-east-2 "$@"; }
region=$(aws_local configure get region)
[[ $region == us-east-2 ]] || fail 'Região do perfil divergente'
# Credential JSON is consumed only in memory, never emitted or saved.
aws_local configure export-credentials --format process | python3 -c '
import json,sys,datetime
c=json.load(sys.stdin)
assert c.get("SessionToken"), "Credenciais temporárias obrigatórias"
e=c.get("Expiration")
assert e, "Expiração obrigatória"
assert datetime.datetime.fromisoformat(e.replace("Z","+00:00")) > datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(minutes=30), "Sessão curta demais"
' || fail 'Renove as credenciais temporárias do perfil'
account=$(aws_local sts get-caller-identity --query Account --output text)
[[ $account == 797781632267 ]] || fail 'Conta AWS divergente'
docker info >/dev/null 2>&1 || fail 'Docker indisponível'
repo=797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api
uri=$(aws_local ecr describe-repositories --repository-names oficina-staging-api --query 'repositories[0].repositoryUri' --output text)
[[ $uri == "$repo" ]] || fail 'ECR divergente'
mutability=$(aws_local ecr describe-repositories --repository-names oficina-staging-api --query 'repositories[0].imageTagMutability' --output text)
[[ $mutability == IMMUTABLE ]] || fail 'ECR deve ter tags imutáveis sem exceções'
ssm=$(aws_local ssm describe-instance-information --filters Key=InstanceIds,Values=i-0ab373b1ba5c14b3e --query 'InstanceInformationList[0].PingStatus' --output text)
[[ $ssm == Online ]] || fail 'Instância não está online no SSM'
printf 'Plano somente leitura: commit=%s tag=git-%s\n' "$commit" "$commit"
printf 'Imagem planejada: %s:git-%s\n' "$repo" "$commit"
printf '%s\n' 'Etapas futuras: confirmar operador; build; login ECR por stdin; push; consultar imageDigest; render.sh; preflight Kubernetes; aplicar por transporte SSM validado; validar rollout.'
[[ -f scripts/staging/render.sh ]] || fail 'render.sh ausente; nenhuma publicação permitida'
fail 'Transporte SSM e preflight Kubernetes não comprovados; execução bloqueada antes de build/login/push/deploy'
