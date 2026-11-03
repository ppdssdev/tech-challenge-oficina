# Observabilidade privada do AWS staging

Prometheus e Grafana são instalados manualmente no K3s single-node, namespace
`oficina-staging`. O Kustomize independente em `k8s/staging/observability` **não é
referenciado pelo deploy da API**. Esta mudança não executa deploy, não altera
API/HPA/RDS/Lambda/Secrets existentes/Terraform e não inclui OpenTelemetry ou Tempo.

## Recursos e coleta

- Prometheus 3.5.5 e Grafana 12.4.13, fixados por versão **e digest linux/amd64**.
  Os pods exigem `kubernetes.io/arch=amd64` e `kubernetes.io/os=linux`.
- Descoberta `role: pod`, somente `oficina-staging`, label `app=oficina-api`, pods
  Running/Ready, porta declarada `http:8080`. O endereço descoberto é o **IP de cada
  pod**; não é o Service balanceado. Scrape `/actuator/prometheus` a cada 15s,
  timeout 10s, labels `namespace`, `pod`, `instance`. Um target distinto por pod
  elegível; a quantidade acompanha o HPA (1–3).
- Role exclusivamente com `list/watch pods` no namespace. Só o Prometheus
  monta token de ServiceAccount. Não há acesso a Secrets, Nodes ou outros namespaces.
- Requests por componente: CPU 50m, RAM 128Mi. Limites: CPU 500m por componente,
  Prometheus 768Mi, Grafana 384Mi (total máximo configurado: 1152Mi).
- PVC Prometheus RWO `local-path`, 2Gi; estratégia Recreate evita dois escritores.
  Retenção `24h` e `1GB` (unidade binária do Prometheus, 1 GiB), prevalecendo o
  primeiro limite atingido. **Retenção não é quota rígida do diretório**: limpeza
  acontece por blocos; WAL/head e compactação precisam de margem. O PVC reserva 2Gi;
  o provisionador local-path não garante uma quota física de disco por diretório.
  Acompanhar disco da EC2 e uso real; não há backup nem HA neste single-node.
- Grafana usa `emptyDir` limitado a 256Mi. Dashboards/datasource vêm do Git e
  credenciais do Secret; usuários extras, sessões e mudanças via UI são efêmeros.
  A recriação limpa o SQLite, simplificando rollback entre versões. Alterações de
  configuração geram ConfigMaps com hash e novo rollout. ConfigMaps anteriores
  são preservados para rollback; não usar `apply --prune`.
- Ambos usam filesystem raiz read-only, usuário não root, seccomp RuntimeDefault,
  nenhuma capability e nenhuma escalada de privilégio. Services somente ClusterIP.
  Não há Ingress, LoadBalancer, NodePort, hostPort, NAT ou recurso de infraestrutura.

O dashboard `oficina-api-staging` inclui scrape por pod e total saudável (comparar
com as réplicas desejadas pelo Deployment/HPA), requisições/s e latência média
ponderadas pelas requisições dos pods,
CPU do processo por pod, heap JVM por pod e outbox pending/processing/sent/failed.
As requisições excluem `/actuator.*` para não contar scrapes/probes. Sem tráfego, a
latência pode ficar sem dados. Os gauges de outbox consultam o mesmo banco em cada
réplica: usa-se `max`, não soma; podem divergir momentaneamente entre scrapes.
Não são contadores de transições nem métricas de CPU/RAM do host. Pods não Ready
saem da descoberta; compare o total saudável com o Deployment/HPA para detectar perdas.
O instalador nunca calcula o esperado a partir dos targets encontrados.

## Pré-requisitos

Na EC2: K3s e StorageClass `local-path` já existentes, namespace/API já saudáveis,
saída existente para os registries, Bash, Python 3, curl, flock e `sudo k3s kubectl`
com Kustomize. Para transferir: tar, gzip, base64 e sha256sum em ambas as máquinas.
Git na EC2 é usado somente para conferir o SHA do cabeçalho tar, sem checkout,
acesso remoto ou credenciais Git.
Não é necessário Docker, PyYAML, acesso GitHub ou AWS CLI na EC2 com o
pacote revisado transferido pela sessão SSM existente. Não criar
NAT, endpoints, portas ou novas regras para instalar esta stack.

Na estação: AWS CLI e Session Manager Plugin, credenciais/permissões SSM existentes
para a instância, SSM Agent ativo. O operador que roda `kubectl` precisa poder criar
os recursos listados e ler pods/Deployment no namespace; o kubeconfig root do K3s atende, mas
não deve ser copiado para a estação. Use o mesmo contexto em todos os comandos.

Pré-requisitos reportados pelo operador via SSM para esta preparação: API 3/3
Ready, zero reinícios; RAM disponível 5,7 GiB e disco disponível 25 GB;
StorageClass `local-path`; K3s em `/usr/local/bin/k3s`; Python3, curl, flock e Git
disponíveis. `kubectl` não está no PATH e checkout em `/opt` não foi confirmado.
HPA 1–3, atualmente 3, memória 79% para alvo 75%. Essa é uma amostra do operador,
não garantia de estabilidade futura: o instalador verifica novamente antes de aplicar.

## Preparar os arquivos — UBUNTU LOCAL

Os comandos operacionais abaixo são para execução futura após revisão. Não há
necessidade de checkout na EC2 nem de diretório `/opt`. Use somente a sessão SSM
interativa já existente; não criar bucket, endpoint, regra de rede ou recurso AWS.

Configure na estação os valores do ambiente e o **SHA completo aprovado da PR**:

```bash
set -euo pipefail
export AWS_PROFILE='<perfil-staging>' AWS_REGION='<regiao>'
export INSTANCE_ID='i-<instancia-k3s>'
export REVIEWED_SHA='<SHA-completo-revisado>'
# No repositório local, com esse commit já disponível:
test "$(git rev-parse "$REVIEWED_SHA^{commit}")" = "$REVIEWED_SHA"
BUNDLE_DIR=$(mktemp -d)
export BUNDLE_DIR
# Allowlist: somente manifests, scripts operacionais e este runbook revisados.
git archive --format=tar "$REVIEWED_SHA" \
  k8s/staging/observability \
  scripts/staging/observability/install.sh \
  scripts/staging/observability/check-targets.py \
  scripts/staging/observability/create-admin-secret.py \
  scripts/staging/observability/k3s-kubectl.sh \
  docs/staging/observability.md >"$BUNDLE_DIR/reviewed.tar"
gzip -n "$BUNDLE_DIR/reviewed.tar"
printf 'SHA revisado: %s\n' "$REVIEWED_SHA"
sha256sum "$BUNDLE_DIR/reviewed.tar.gz"
base64 -w 76 "$BUNDLE_DIR/reviewed.tar.gz" >"$BUNDLE_DIR/reviewed.tar.gz.b64"
cat "$BUNDLE_DIR/reviewed.tar.gz.b64"
aws ssm start-session --target "$INSTANCE_ID"
```

Copie o SHA, o checksum e o bloco base64 emitidos localmente. O pacote não contém
Secrets, `.git`, kubeconfig, código Java nem arquivos não commitados. O SHA
identifica a origem revisada; o SHA-256 abaixo verifica os bytes transferidos.

## Receber e conferir — EC2 VIA SSM

Na sessão interativa, execute Bash (`bash`), depois:

```bash
set -euo pipefail
umask 077
REVIEW_DIR=$(mktemp -d "$HOME/oficina-observability.XXXXXXXX")
export REVIEW_DIR
cd "$REVIEW_DIR"
read -r -p 'SHA completo revisado (da estação): ' REVIEWED_SHA
read -r -p 'SHA-256 do pacote (da estação): ' BUNDLE_SHA256
[[ "$REVIEWED_SHA" =~ ^[0-9a-f]{40}$ ]]
[[ "$BUNDLE_SHA256" =~ ^[0-9a-f]{64}$ ]]
cat > reviewed.tar.gz.b64 <<'OFICINA_REVIEWED_BUNDLE'
<cole aqui somente o bloco base64 gerado na estação>
OFICINA_REVIEWED_BUNDLE
base64 --decode reviewed.tar.gz.b64 > reviewed.tar.gz
printf '%s  reviewed.tar.gz\n' "$BUNDLE_SHA256" | sha256sum --check --strict -
gzip --decompress --stdout reviewed.tar.gz > reviewed.tar
test "$(git get-tar-commit-id < reviewed.tar)" = "$REVIEWED_SHA"
tar -xf reviewed.tar --no-same-owner
printf '%s\n' "$REVIEWED_SHA" > REVIEWED_SHA
printf 'Diretório revisado: %s\n' "$REVIEW_DIR"
export KUBECTL="$REVIEW_DIR/scripts/staging/observability/k3s-kubectl.sh"
test -x "$KUBECTL"
sudo "$KUBECTL" version --client
sudo "$KUBECTL" -n oficina-staging get deployment oficina-api
sudo "$KUBECTL" -n oficina-staging get hpa
```

Pare se o checksum falhar. Anote o diretório impresso para reutilizá-lo nas outras
sessões. `KUBECTL` é **um caminho executável**, nunca `"sudo k3s kubectl"`.
O wrapper revisado executa `/usr/local/bin/k3s kubectl "$@"`, sem depender do PATH;
o `sudo` envolve o helper/instalador. `K3S` pode indicar outro caminho executável
nos testes, sem `eval` ou divisão de uma string de comando.
Assim ambos usam o mesmo acesso root do K3s, sem copiar ou imprimir kubeconfig.

## Secret e instalação — EC2 VIA SSM

Crie apenas o Secret novo, uma vez. Gere uma senha aleatória de ao menos 20
caracteres em um cofre/gerenciador de senhas e guarde-a antes de colar no prompt:

```bash
cd "$REVIEW_DIR"
# Consulta somente o nome; erro de acesso interrompe o procedimento.
SECRET_NAME=$(sudo "$KUBECTL" -n oficina-staging get secret \
  oficina-observability-grafana-admin --ignore-not-found -o name)
if [[ -z "$SECRET_NAME" ]]; then
  sudo env KUBECTL="$KUBECTL" \
    python3 scripts/staging/observability/create-admin-secret.py
else
  printf 'Secret existente preservado; criação dispensada.\n'
fi
```

O prompt usa `getpass` sem eco; senha não aparece em stdout, argv, histórico,
arquivo temporário ou `last-applied`. Usuário: `oficina-operator`. O helper recusa
entrada sem terminal e usa **create**, nunca altera um Secret existente. Em uma
reexecução, pule essa criação se o Secret já existir. Não coloque senha em comandos
SSM Run Command, parâmetros AWS, variáveis `--from-literal`, Git ou logs. O Secret
Kubernetes depende das proteções de RBAC/criptografia já configuradas no K3s.

Instale (a mesma operação pode ser repetida):

```bash
sudo env KUBECTL="$KUBECTL" EXPECTED_API_PODS=3 \
  bash scripts/staging/observability/install.sh install
```

O instalador faz preflight, rejeita colisões com recursos não gerenciados, renderiza
somente a stack, salva snapshot sem Secrets em `/var/lib/oficina-observability`,
valida com server-side dry-run e aplica com field manager próprio. Aguarda os dois
rollouts e consulta `/api/v1/targets` por port-forward temporário em loopback.

`EXPECTED_API_PODS=3` é o padrão e o valor para a amostra atual. Exige também que
esse valor corresponda ao desejado pelo Deployment; não use uma contagem menor
para contornar pods/targets ausentes. O modo opcional `auto` permanece compatível
com os testes, mas não é usado neste procedimento. Nenhum modo altera réplicas/HPA.
O Deployment precisa estar convergido (geração observada, todas as réplicas
atualizadas, Ready e disponíveis). Zero pods/targets nunca é sucesso.

Antes de qualquer `apply` (inclusive dry-run), o instalador exige duas amostras
da API com os mesmos nomes, UIDs, IPs, geração do Deployment e contadores de
reinício, separadas por 15s. Se a API não estabilizar em três pods, interrompe
sem aplicar nem remover recursos da stack. O Secret criado separadamente permanece.
Não é preciso ter Prometheus instalado para esse preflight.

O aceite exige correspondência exata entre pods Running/Ready não terminando e
targets UP: nomes, IPs distintos, namespace, intervalo 15s e URLs individuais
`http://<IP>:8080/actuator/prometheus`. Lê Deployment/pods antes e depois da consulta
e exige duas amostras válidas com as mesmas identidades e contadores de reinício, separadas por
15s. Scaling/rollout ou falha reinicia a estabilidade; o HPA permanece livre.
São até 24 tentativas em cada verificação (pausa 5s em falhas, 15s entre amostras
válidas), além dos rollouts de até cinco minutos cada. Falhas após o início da
aplicação acionam rollback;
investigue API/HPA/targets e repita depois, sem reduzir o esperado para forçar aceite.
O bloqueio `flock` impede instalações simultâneas.

## Rollback

Falhas de apply/rollout/targets e sinais INT/TERM iniciam rollback automático.
Em atualização, restaura os objetos anteriores (incluindo ConfigMaps e templates)
e valida novamente rollouts/targets. Na primeira instalação, remove somente
objetos novos da stack, **preservando PVC e Secret**. O script retorna falha mesmo
quando o rollback foi bem-sucedido. Não restaura dados históricos do Prometheus.

O caminho do snapshot aparece antes da aplicação. Para reverter manualmente a
última instalação, na EC2 via SSM:

```bash
cd "$REVIEW_DIR"
sudo env KUBECTL="$KUBECTL" EXPECTED_API_PODS=3 \
  bash scripts/staging/observability/install.sh rollback \
  '/var/lib/oficina-observability/attempt.<identificador-impresso>'
```

Use o snapshot correspondente à instalação que deseja reverter. Ele pode conter
configuração operacional, portanto mantém permissões restritas. SIGKILL, queda
da EC2 ou desconexão que mate o processo abruptamente podem impedir rollback;
nesse caso execute o comando manual após restabelecer o acesso. Se o próprio
rollback falhar, o script informa o snapshot para recuperação; não apaga estado,
PVC nem Secret. Não usar `kubectl delete -k`, pois incluiria o PVC e perderia dados.

## Aceite runtime e acesso privado ao Grafana

O aceite runtime só é concluído na futura instalação: instalador com sucesso,
rollouts saudáveis, targets individuais da amostra estável e Grafana acessível
pelo túnel com dashboard/datasource provisionados. Não foi executado nesta revisão.

**EC2 VIA SSM**, no primeiro terminal (redefina `REVIEW_DIR` com o caminho anotado
se abrir outra sessão), confira os rollouts e deixe o port-forward rodando:

```bash
sudo /usr/local/bin/k3s kubectl -n oficina-staging rollout status deployment/oficina-api --timeout=300s
sudo /usr/local/bin/k3s kubectl -n oficina-staging rollout status deployment/oficina-observability-prometheus --timeout=300s
sudo /usr/local/bin/k3s kubectl -n oficina-staging rollout status deployment/oficina-observability-grafana --timeout=300s
sudo /usr/local/bin/k3s kubectl -n oficina-staging port-forward --address 127.0.0.1 \
  service/oficina-observability-grafana 13000:3000
```

**UBUNTU LOCAL**, no segundo terminal com as variáveis AWS configuradas, abra o túnel:

```bash
aws ssm start-session --target "$INSTANCE_ID" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["13000"],"localPortNumber":["3000"]}'
```

**UBUNTU LOCAL**, em outro terminal, valide `curl --fail --silent --show-error
http://127.0.0.1:3000/api/health` (database `ok`). Abra
`http://127.0.0.1:3000/d/oficina-api-staging` e autentique com
`oficina-operator` e a senha do cofre. O transporte até a EC2 passa pelo SSM;
não abra portas 3000/13000/9090 no security group. Confirme datasource Prometheus
e séries por pod no dashboard; ausência de tráfego pode deixar latência sem dados.
Prometheus continua ClusterIP, acessado pelo Grafana e pelo port-forward temporário
exclusivamente em loopback do instalador. Encerre ambos com Ctrl+C.
Se o pod for recriado, reinicie o port-forward. Rotação de senha exige procedimento
administrativo próprio; alterar o Secret não atualiza automaticamente um SQLite
já inicializado. O instalador não modifica o Secret.

## Validação sem deploy

Com Docker, kubectl, ShellCheck e Python com PyYAML 6.0.2 na estação:

```bash
bash scripts/staging/observability/validate.sh
```

A validação cobre Kustomize, schemas Kubernetes 1.34 em modo strict, `promtool`
(configuração e PromQL do dashboard), sintaxe Bash, ShellCheck, políticas de
segurança/escopo e testes simulados de instalação idempotente e recuperação.
As imagens/schemas podem ser baixados; nenhum kubeconfig/cluster/AWS é consultado.
O workflow `observability-validate.yml` repete essas verificações em PRs relevantes.
Os testes simulados não substituem o aceite runtime de PVC, agendamento, discovery,
RBAC e targets reais, realizado pelo instalador somente na futura instalação.

Validação desta revisão: 25 testes aprovados, incluindo preflight antes de apply,
padrão de três pods, wrapper K3s no instalador e helper de Secret, compatibilidade
do modo auto com 1–3 pods, rejeição de amostras instáveis/reinícios e de override
que ocultaria ausência; 11 recursos válidos no kubeconform, zero inválidos/erros/
ignorados; configuração Prometheus e 10 expressões PromQL aprovadas pelo promtool;
Bash/ShellCheck aprovados. Execução local com kubectl 1.35.10 (schemas 1.34.0),
PyYAML 6.0.1 e ShellCheck 0.11.0; o workflow mantém kubectl 1.34.0/PyYAML 6.0.2.
O smoke test Docker do Grafana relatado na versão original da PR não foi repetido
nesta revisão. Não houve deploy nem consulta ao staging; aceite runtime pendente.

Referências oficiais: [retenção Prometheus](https://prometheus.io/docs/prometheus/latest/storage/),
[configuração por arquivo de segredo Grafana](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/),
[port forwarding SSM](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html).
