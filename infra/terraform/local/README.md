# Terraform local com Kind

Este módulo provisiona o ambiente de demonstração inteiramente na máquina local. Ele não usa Terraform Cloud, backend remoto, AWS, EKS ou qualquer outro serviço de cloud ou ferramenta paga.

O Terraform:

1. cria o cluster Kind `oficina-local`;
2. builda a imagem `tech-challenge-oficina:local` com o Docker local;
3. carrega a imagem diretamente no cluster, sem registry externo;
4. executa `kubectl apply -k` sobre `k8s/local`;
5. aguarda os Deployments do PostgreSQL, Mailpit, API, Prometheus e Grafana.

Como `k8s/local` é a fonte de verdade e inclui a stack de observabilidade e o HPA da API, `terraform apply` também sobe Prometheus, Grafana e o `oficina-api-hpa` sem recursos HCL duplicados.

Os manifests de `k8s/local` continuam sendo a fonte de verdade dos recursos Kubernetes. Eles não foram duplicados em HCL. O arquivo `k8s/kind/oficina-kind.yaml` também é lido pelo módulo e convertido para o formato aceito pelo provider Kind.

O estado é armazenado somente no arquivo local `terraform.tfstate`, ignorado pelo Git. Como o state contém as credenciais temporárias de acesso ao cluster, ele deve permanecer local e com acesso restrito. As credenciais presentes em `k8s/local/secret.yaml` são valores previsíveis exclusivos da demonstração local, não secrets de produção.

## Pré-requisitos

- Docker com o daemon em execução
- Terraform 1.6 ou superior
- `kubectl`
- `kind`, usado também pelos scripts diretos e para diagnóstico local

## Escolher um único fluxo

O fluxo Terraform e os scripts diretos do Script 7 gerenciam o mesmo nome de cluster. Antes do primeiro `terraform apply`, remova um cluster `oficina-local` criado manualmente:

```bash
./scripts/k8s-destroy-local.sh
```

Depois disso, deixe o Terraform criar e destruir esse ambiente. O Terraform não adota automaticamente um cluster Kind já existente.

## Provisionar

Na raiz do projeto:

```bash
cd infra/terraform/local
terraform init
terraform fmt -recursive
terraform validate
terraform plan
terraform apply
```

Em outro terminal, a partir da raiz do projeto, também existe um atalho que executa `init` e `apply -auto-approve`:

```bash
./scripts/terraform-local-apply.sh
```

Após usar o atalho, obtenha o kubeconfig a partir da raiz com:

```bash
export KUBECONFIG="$(terraform -chdir=infra/terraform/local output -raw kubeconfig_path)"
```

Alterações no `Dockerfile`, `pom.xml` ou em `src` refazem o build e o carregamento da imagem no próximo `terraform apply`. Alterações nos arquivos de `k8s/local` reaplicam o Kustomize automaticamente.

## Validar e acessar

O módulo grava um kubeconfig isolado dentro do diretório `.terraform`. No terminal usado para os comandos operacionais, selecione-o sem alterar seu contexto Kubernetes global:

```bash
export KUBECONFIG="$(terraform output -raw kubeconfig_path)"
```

Confirme os recursos:

```bash
kubectl -n oficina get pods
kubectl -n oficina get svc
kubectl -n oficina rollout status deployment/oficina-api --timeout=300s
kubectl -n oficina rollout status deployment/oficina-prometheus --timeout=180s
kubectl -n oficina rollout status deployment/oficina-grafana --timeout=180s
kubectl -n oficina get hpa oficina-api-hpa
```

O HPA escala o Deployment `oficina-api` de 1 a 3 réplicas com alvos de utilização média de 70% de CPU e 75% de memória. Para que o Kind forneça essas métricas, instale o Metrics Server a partir da raiz do projeto, depois de selecionar o kubeconfig gerado pelo Terraform:

```bash
./scripts/k8s-install-metrics-server.sh
kubectl -n oficina top pods
kubectl -n oficina get hpa
kubectl -n oficina describe hpa oficina-api-hpa
```

Sem Metrics Server, o Terraform ainda aplica o HPA, mas as métricas podem aparecer como `<unknown>`. Em um cluster cloud real, o serviço de métricas de recursos precisa estar habilitado. O HPA deste projeto escala apenas a API; PostgreSQL e os demais componentes não fazem parte do alvo de autoscaling.

Mantenha o port-forward da API em um terminal:

```bash
kubectl -n oficina port-forward svc/oficina-api 8080:8080
```

Em outro terminal:

```bash
curl http://localhost:8080/actuator/health
```

Para acessar o Mailpit, mantenha outro port-forward ativo e abra `http://localhost:8025`:

```bash
kubectl -n oficina port-forward svc/oficina-mailpit 8025:8025
```

Prometheus e Grafana também são acessados somente por port-forward:

```bash
kubectl -n oficina port-forward svc/oficina-prometheus 9090:9090
kubectl -n oficina port-forward svc/oficina-grafana 3000:3000
```

Depois, acesse `http://localhost:9090` e `http://localhost:3000`. O Grafana usa as credenciais locais `admin` / `admin`.

O port-forward permanece manual e também pode ser iniciado pelo script existente:

```bash
./scripts/k8s-port-forward.sh
```

O script respeita a variável `KUBECONFIG` exportada acima.

Os outputs `prometheus_url`, `grafana_url`, `prometheus_port_forward_command` e `grafana_port_forward_command` documentam esses acessos, junto aos outputs existentes da API e do Mailpit. Os outputs `hpa_status_command`, `hpa_describe_command` e `top_pods_command` fornecem os comandos de diagnóstico do autoscaling. As URLs pressupõem que os respectivos port-forwards estejam ativos.

## Destruir

Dentro de `infra/terraform/local`:

```bash
terraform destroy
```

Ou, a partir da raiz:

```bash
./scripts/terraform-local-destroy.sh
```

Durante o destroy, o Terraform remove os manifests com `kubectl delete -k` antes de excluir o cluster Kind. A imagem Docker local permanece no daemon e pode ser reutilizada em outro provisionamento.
