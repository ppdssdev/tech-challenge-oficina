# Release local de staging — preparação bloqueada

Auditoria do checkout base `7b47790`: o `render.sh` citado na solicitação não
existe neste checkout. Não foi encontrado documento SSM versionado, transporte
validado de manifests nem procedimento de autenticação ECR do nó. A estratégia
em `docs/fase3/cicd-strategy.md` apenas propõe documento fixo/allowlist. Não se
pode comprovar os pré-requisitos Kubernetes sem esse acesso. Esta entrega é um
preflight parcial, não um fluxo de publicação operacional.

O Dockerfile usa Java 21, Maven multi-stage, usuário sem root e diretório de logs
restritos. Suas imagens base não estão fixadas por digest e dependências de pacote
podem mudar: rebuilds do mesmo commit não são garantidamente idênticos. A CI atual
executa Maven, build Docker e validação de schemas Kubernetes; permanece intacta.
O overlay staging contém Deployment, Service e Namespace; não contém HPA e fixa
replicas=1. Aplicá-lo integralmente pode interferir na configuração existente.
O Job de migração é separado; Flyway do runtime está desligado. Nenhum desses
recursos foi alterado por esta entrega.

## Uso atual

Em checkout limpo, com Bash, Git, Python 3, Docker e AWS CLI v2 compatível com
`configure export-credentials`, renove a sessão temporária do perfil
`oficina-staging` pelo mecanismo local já autorizado. O script exige token de
sessão e expiração superior a 30 minutos. Não persiste nem imprime credenciais.
Não use `bash -x`, gravação de terminal ou debug da AWS CLI.

```bash
scripts/staging/release-local.sh --dry-run
```

O padrão é dry-run. Consultas AWS são somente leitura, mas exigem acesso à AWS.
Nesta tarefa elas foram substituídas por mocks. O plano usa `git-<SHA completo>`;
exige conta `797781632267`, região `us-east-2`, ECR correto com política IMMUTABLE,
Docker disponível, checkout limpo e instância `i-0ab373b1ba5c14b3e` online no SSM.
Falhas e dependências não comprovadas retornam status diferente de zero.

Comando reservado para revisão humana futura:

```bash
scripts/staging/release-local.sh --execute
```

Atualmente esse comando também falha antes de build, login, push ou deploy.
Não há opção para ignorar o bloqueio. Não há confirmação do operador porque
nenhuma operação mutante está habilitada. Uma implementação futura deve obter
confirmação interativa após concluir **todos** os preflights e antes da primeira
mutação; stdin redirecionado não deve aprovar automaticamente.

## Dependências para completar o fluxo

1. Recuperar o renderizador original e seu contrato de argumentos da revisão
   correta, sem incorporar ou modificar as quatro PRs OIDC.
2. Identificar o documento SSM aprovado, versão/hash, parâmetros permitidos,
   transporte existente e evidência de validação. Não passar credenciais,
   kubeconfig ou manifests inline em Run Command, nem inventar bucket/transporte.
3. Pelo canal aprovado, verificar namespace, Deployment/container `api`, acesso
   ao ECR no nó, arquitetura, métricas/HPA, probes, capacidade, Secrets e ConfigMaps
   referenciados. Consultar somente existência/chaves necessárias, nunca imprimir
   valores. Registrar digest anterior e configuração não sensível da release.
4. Após confirmação, construir do commit limpo para a arquitetura comprovada,
   usar configuração Docker temporária com permissões restritas, autenticar por
   stdin, publicar a tag imutável e consultar `imageDigest` no ECR. Se a tag já
   existir, não sobrescrever: verificar proveniência antes de reutilizar. Exigir
   formato `sha256:` com 64 hexadecimais e usar o renderizador recuperado.
5. Revisar diff contra o cluster: preservar HPA, replicas geridas pelo HPA,
   Secrets, ConfigMaps e configurações existentes. Nunca usar prune/delete ou
   aplicar Job de migração. Mudanças de schema exigem aprovação independente.
6. Aplicar exclusivamente pelo mecanismo comprovado, verificar status/timeout do
   comando SSM e rollout. Falha deve interromper a promoção sem rollback automático.

## Validação e rollback pelo canal SSM aprovado

Os comandos abaixo são verificações no nó, não um mecanismo de transporte:

```bash
sudo k3s kubectl -n oficina-staging rollout status deployment/oficina-api --timeout=300s
sudo k3s kubectl -n oficina-staging get deployment oficina-api -o jsonpath='{.spec.template.spec.containers[?(@.name=="api")].image}'
sudo k3s kubectl -n oficina-staging get pods -l app=oficina-api -o wide
sudo k3s kubectl -n oficina-staging get pods -l app=oficina-api -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.containerStatuses[*].imageID}{" "}{.status.containerStatuses[*].ready}{"\n"}{end}'
sudo k3s kubectl -n oficina-staging get hpa
```

Comparar imagem desejada e imageID de todos os pods com digest publicado (considerar
manifest list e digest da arquitetura). Exigir todos os pods novos Ready, probes
configuradas e saudáveis, sem reinícios crescentes; HPA com métricas disponíveis e
condições saudáveis. Validar readiness/liveness pelo canal privado já aprovado.
Não abrir portas públicas nem imprimir logs que possam conter dados sensíveis.

Para rollback, confirmar disponibilidade do digest anterior no ECR e compatibilidade
do código anterior com schema atual; obter aprovação explícita. Recuperar manifests
anteriores revisados, renderizar com digest anterior e aplicar pelo mesmo canal
validado, preservando configurações atuais. Repetir todas as verificações. Não
reverter banco automaticamente, não depender de tag mutável ou de `rollout undo`
sem verificar sua revisão. Não alterar states, Terraform, RDS, Lambda, IAM ou Secrets
Manager; não destruir Namespace, HPA, Secrets ou ConfigMaps.

## Verificação local

```bash
python3 -m unittest discover -s tests/staging -v
bash -n scripts/staging/release-local.sh
shellcheck scripts/staging/release-local.sh
kubectl kustomize k8s/staging
```

Mocks cobrem conta/região, estado Git, ECR mutável, SSM offline, argumentos,
identificador imutável e ausência de comandos mutantes em ambos os modos.
Não comprovam credenciais reais, IAM, conectividade nem comportamento do cluster.
