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
temporária no perfil oficina-staging com pelo menos 60 segundos restantes, conta/região certas,
ECR IMMUTABLE sem exceções, tag ainda ausente e SSM Online. Exige terminal e a
palavra PUBLICAR antes de construir ou autenticar. Constrói somente arquivos do
commit via git archive para linux/amd64; confirmar compatibilidade do nó antes
de promover. Bases Docker e pacotes não são fixados por digest: rebuild não é
bit a bit reproduzível; a tag git-<SHA completo> nunca é sobrescrita. Uma tag já
existente bloqueia repetição, inclusive após falha posterior ao push.

Use `aws login --profile oficina-staging` para iniciar a sessão. As credenciais
de 15 minutos são aceitas; cada chamada resolve novamente o perfil para permitir
renovação automática. A sessão e conta são revalidadas após o build, antes do push.
O digest retornado pelo ECR deve corresponder ao digest informado pelo push.

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

## Promoção e rollback manuais por SSM

A publicação ECR não promove a aplicação. O procedimento AWS-RunShellScript
existente foi revisado em [Promoção manual da API](promotion-manual.md), incluindo
validação de conta/região/perfil/instância, captura do digest e das três variáveis
anteriores, patch único, espera de resultado SSM/rollout, imagem efetiva, probes,
aceite JSON/Tempo/métricas e rollback de digest **e configuração**.

A configuração [tracing-demo.env](../../scripts/staging/tracing-demo.env) é somente
opt-in para a janela da demonstração, após o aceite Tempo da sessão 1. Nenhum
manifest a importa automaticamente. Execução manual depende da revisão operacional;
nenhum comando remoto foi executado nesta entrega. Não é CD automático pelo GitHub.

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
