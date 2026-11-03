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
  timeout 10s, labels `namespace`, `pod`, `instance`. Esperados três targets distintos.
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

O dashboard `oficina-api-staging` inclui scrape por pod e total saudável (esperado
3), requisições/s e latência média ponderadas pelas requisições dos três pods,
CPU do processo por pod, heap JVM por pod e outbox pending/processing/sent/failed.
As requisições excluem `/actuator.*` para não contar scrapes/probes. Sem tráfego, a
latência pode ficar sem dados. Os gauges de outbox consultam o mesmo banco em cada
réplica: usa-se `max`, não soma; podem divergir momentaneamente entre scrapes.
Não são contadores de transições nem métricas de CPU/RAM do host. Pods não Ready
saem da descoberta; o total saudável evidencia a perda em relação aos três esperados.

## Pré-requisitos

Na EC2: K3s e StorageClass `local-path` já existentes, namespace/API já saudáveis,
saída existente para os registries, Bash, Python 3, curl, flock e kubectl com
Kustomize. Não é necessário Docker, PyYAML, acesso GitHub ou AWS CLI na EC2 se o
checkout revisado tiver sido transferido por mecanismo já disponível. Não criar
NAT, endpoints, portas ou novas regras para instalar esta stack.

Na estação: AWS CLI e Session Manager Plugin, credenciais/permissões SSM existentes
para a instância, SSM Agent ativo. O operador que roda `kubectl` precisa poder criar
os recursos listados e ler pods no namespace; o kubeconfig root do K3s atende, mas
não deve ser copiado para a estação. Use o mesmo contexto em todos os comandos.

## Instalação manual via SSM

Os comandos abaixo são para execução futura, depois da revisão/merge. Defina os
valores reais na estação; não há ID de instância, conta ou credencial no Git:

```bash
export AWS_PROFILE='<perfil-staging>' AWS_REGION='<regiao>'
export INSTANCE_ID='i-<instancia-k3s>'
aws ssm start-session --target "$INSTANCE_ID"
```

Dentro da sessão interativa na EC2, use um checkout de `main` no **SHA revisado**,
por exemplo `/opt/tech-challenge-oficina`. Se Git/acesso ao repositório já estiverem
disponíveis, faça fetch e checkout desse SHA; alternativamente transfira um arquivo
`git archive` pelo canal privado existente. Não instalar dependências via `curl | sh`.

Crie apenas o Secret novo, uma vez. Gere uma senha aleatória de ao menos 20
caracteres em um cofre/gerenciador de senhas e guarde-a lá antes de colar no prompt:

```bash
cd /opt/tech-challenge-oficina
sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  python3 scripts/staging/observability/create-admin-secret.py
```

O prompt usa `getpass` sem eco; senha não aparece em stdout, argv, histórico,
arquivo temporário ou `last-applied`. Usuário: `oficina-operator`. O helper recusa
entrada sem terminal e usa **create**, nunca altera um Secret existente. Em uma
reexecução, pule essa criação se o Secret já existir. Não coloque senha em comandos
SSM Run Command, parâmetros AWS, variáveis `--from-literal`, Git ou logs. O Secret
Kubernetes depende das proteções de RBAC/criptografia já configuradas no K3s.

Instale (a mesma operação pode ser repetida):

```bash
sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  bash scripts/staging/observability/install.sh install
```

O instalador faz preflight, rejeita colisões com recursos não gerenciados, renderiza
somente a stack, salva snapshot sem Secrets em `/var/lib/oficina-observability`,
valida com server-side dry-run e aplica com field manager próprio. Aguarda os dois
rollouts e consulta `/api/v1/targets` por port-forward temporário em loopback.
Só conclui com três pods Ready distintos, três IPs distintos, três targets UP,
intervalo 15s e URLs exatas `http://<IP>:8080/actuator/prometheus`. Há até dois minutos
de tentativas após os rollouts (cada rollout tem timeout de cinco minutos).
O bloqueio `flock` impede instalações simultâneas. `EXPECTED_API_PODS` permite
ajuste explícito caso o HPA passe a manter outro número; o default é 3.

Para automatizar a execução futura **sem transportar senha**, após preparar checkout
no SHA revisado e Secret, pode-se usar Run Command a partir da estação:

```bash
cat > /tmp/oficina-observability-ssm.json <<'JSON'
{"commands":["set -eu", "cd /opt/tech-challenge-oficina", "export KUBECONFIG=/etc/rancher/k3s/k3s.yaml", "bash scripts/staging/observability/install.sh install"],"executionTimeout":["1800"]}
JSON
COMMAND_ID=$(aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --parameters file:///tmp/oficina-observability-ssm.json \
  --query 'Command.CommandId' --output text)
aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID"
```

Acompanhe até `Status=Success`; o retorno inicial de send-command não comprova
instalação. O comando usa o checkout já preparado, sem atualizar a API.

## Rollback

Falhas de apply/rollout/targets e sinais INT/TERM iniciam rollback automático.
Em atualização, restaura os objetos anteriores (incluindo ConfigMaps e templates)
e valida novamente rollouts/targets. Na primeira instalação, remove somente
objetos novos da stack, **preservando PVC e Secret**. O script retorna falha mesmo
quando o rollback foi bem-sucedido. Não restaura dados históricos do Prometheus.

O caminho do snapshot aparece antes da aplicação. Para reverter manualmente a
última instalação, na EC2 via SSM:

```bash
sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  bash scripts/staging/observability/install.sh rollback \
  /var/lib/oficina-observability/attempt.<identificador-impresso>
```

Use o snapshot correspondente à instalação que deseja reverter. Ele pode conter
configuração operacional, portanto mantém permissões restritas. SIGKILL, queda
da EC2 ou desconexão que mate o processo abruptamente podem impedir rollback;
nesse caso execute o comando manual após restabelecer o acesso. Se o próprio
rollback falhar, o script informa o snapshot para recuperação; não apaga estado,
PVC nem Secret. Não usar `kubectl delete -k`, pois incluiria o PVC e perderia dados.

## Acesso privado ao Grafana

No primeiro terminal, abra a sessão SSM e deixe este comando rodando na EC2:

```bash
sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  kubectl -n oficina-staging port-forward --address 127.0.0.1 \
  service/oficina-observability-grafana 13000:3000
```

No segundo terminal **na estação**, abra o túnel SSM para a porta loopback da EC2:

```bash
aws ssm start-session --target "$INSTANCE_ID" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["13000"],"localPortNumber":["3000"]}'
```

Abra `http://127.0.0.1:3000/d/oficina-api-staging` e autentique com
`oficina-operator` e a senha do cofre. O transporte até a EC2 passa pelo SSM;
não abra portas 3000/13000/9090 no security group. Encerre ambos com Ctrl+C.
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

Validação desta PR: 17 testes aprovados; 11 recursos válidos no kubeconform,
zero inválidos/erros/ignorados; configuração Prometheus e 10 expressões PromQL
aprovadas pelo promtool; Bash/ShellCheck aprovados. Também foi executado smoke test
local do Grafana por digest, com rede isolada, 384Mi, usuário não root e filesystem
read-only: health, login com senha aleatória descartável, datasource e dashboard
com 10 painéis aprovados. Não houve deploy nem consulta ao staging.

Referências oficiais: [retenção Prometheus](https://prometheus.io/docs/prometheus/latest/storage/),
[configuração por arquivo de segredo Grafana](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/),
[port forwarding SSM](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html).
