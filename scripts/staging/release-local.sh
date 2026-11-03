#!/usr/bin/env bash
# Offline by default; explicit execution publishes only to ECR, never deploys.
set +x
set -euo pipefail
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }
mode=${1:---dry-run}
[[ $# -le 1 && ( $mode == --dry-run || $mode == --execute ) ]] || fail 'Uso: release-local.sh [--dry-run|--execute]'
for tool in git python3 kubectl; do
  command -v "$tool" >/dev/null || fail "Ferramenta ausente: $tool"
done
root=$(git rev-parse --show-toplevel)
cd "$root"
commit=$(git rev-parse --verify HEAD)
[[ $commit =~ ^[0-9a-f]{40}$ ]] || fail 'Commit inválido'
repo=797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api
if [[ $mode == --dry-run ]]; then
  printf 'DRY-RUN offline: commit=%s tag=git-%s\n' "$commit" "$commit"
  printf '%s\n' 'Digest fictício somente para validação; nenhum build/push/deploy.'
  bash scripts/staging/render.sh "$repo@sha256:$(printf 'a%.0s' {1..64})" >/dev/null
  printf '%s\n' 'Renderização OK. --execute publica imagem após confirmação; deploy automático bloqueado.'
  exit 0
fi
for tool in aws docker tar; do command -v "$tool" >/dev/null || fail "Ferramenta ausente: $tool"; done
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
# Validate renderer before any mutation.
bash scripts/staging/render.sh "$repo@sha256:$(printf 'a%.0s' {1..64})" >/dev/null
# Successful empty result means absent; AWS failures must never mean absent.
existing=$(aws_local ecr list-images --repository-name oficina-staging-api --filter tagStatus=TAGGED --query "imageIds[?imageTag=='git-$commit'].imageDigest" --output text)
[[ -z $existing ]] || fail 'Tag já existe; não reconstruir ou sobrescrever'
[[ -t 0 ]] || fail 'Confirmação exige terminal interativo'
printf 'Publicar %s:git-%s? Digite PUBLICAR (não realiza deploy): ' "$repo" "$commit"
read -r confirmation
[[ $confirmation == PUBLICAR ]] || fail 'Operação cancelada'
umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
# Build only tracked content from the selected commit; no local credentials/context.
git archive "$commit" | tar -x -C "$work"
export DOCKER_CONFIG="$work/.docker"
mkdir "$DOCKER_CONFIG"
docker build --platform linux/amd64 -t "$repo:git-$commit" "$work"
aws_local ecr get-login-password | docker login --username AWS --password-stdin "${repo%%/*}" >/dev/null 2>&1
docker push "$repo:git-$commit"
digest=$(aws_local ecr describe-images --repository-name oficina-staging-api --image-ids "imageTag=git-$commit" --query 'imageDetails[0].imageDigest' --output text)
[[ $digest =~ ^sha256:[a-f0-9]{64}$ ]] || fail 'Digest inválido retornado pelo ECR'
bash scripts/staging/render.sh "$repo@$digest" > "$work/runtime.yaml"
printf 'Imagem publicada: %s@%s\n' "$repo" "$digest"
printf '%s\n' 'Manifest validado em memória temporária. Reproduza com render.sh e o digest acima.' 'Deploy automático BLOQUEADO: permissões e transporte SSM não comprovados.'
