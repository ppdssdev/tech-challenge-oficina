# Revisão de segurança e integração documental — 2026-11-03

Revisão estática adicional à [consolidação documental](README.md), sem substituir
relatórios históricos. Base API: PR [#14](https://github.com/ppdssdev/tech-challenge-oficina/pull/14),
commit `ac5834624873219fc441c6cc54056dfb14baee71`. Nenhum Java, manifesto, banco,
IAM ou Terraform foi alterado nesta frente. Os resultados abaixo não afirmam
execução do fluxo em staging. Estado de GitHub/AWS deve ser coletado novamente
na janela operacional; uma CI verde não comprova comportamento do ambiente.

## Bloqueios e contrato de aceite

| Cenário | Resultado obrigatório | Caminho e limite da evidência |
| --- | --- | --- |
| CUSTOMER consulta sua OS | 200 | `CustomerWorkOrderController.get` usa identidade autenticada e `GetWorkOrderService.getForCustomer`; depende de fixture confirmada e JWT válido. |
| Consulta sem token | 401 | `CustomerSecurityConfig.customerChain`, `@Order(1)`, protege `/api/v1/customer/**`. |
| CUSTOMER acessa ADMIN | 403 | `SecurityConfig.securityFilterChain`, `@Order(2)`, exige ADMIN; autenticação RS256 passa antes pelo verificador PEM legado. O 403 depende de ele autenticar o mesmo JWT. |
| CUSTOMER consulta OS de outro | 404 | `SpringDataWorkOrderRepository.findForCustomer` filtra **id e customerId**; ausência vira `NotFoundException` e `ApiExceptionHandler.handleNotFound` retorna 404. |

**Bloqueio Java identificado, sem correção nesta frente:**

- [CustomerSecurityConfig.java](../../src/main/java/br/com/fiap/techchallenge/oficina/configuration/CustomerSecurityConfig.java)
  usa JWKS HTTPS para `/api/v1/customer/**`, com issuer, audience e contrato RS256.
- [JwtAuthenticationFilter.java](../../src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/security/JwtAuthenticationFilter.java)
  encaminha RS256, inclusive em `/api/v1/admin/**`, a
  [CustomerJwtVerifier.java](../../src/main/java/br/com/fiap/techchallenge/oficina/adapters/out/security/CustomerJwtVerifier.java).
  Esse verificador usa `app.jwt.customer.enabled` e mapa de PEMs por `kid`, sem
  consumir o JWKS da outra cadeia. Se estiver desabilitado ou se chave, `kid`,
  issuer, audience ou contrato divergirem, responde 401 antes da autorização ADMIN.
- [application.yml](../../src/main/resources/application.yml) usa propriedades
  distintas: `CUSTOMER_JWT_*`/`CUSTOMER_JWKS_URI` para JWKS e
  `APP_JWT_CUSTOMER_*`/`public-keys` para PEM. O primeiro funcionar não comprova o segundo.
- [CustomerJwtIntegrationTest.java](../../src/test/java/br/com/fiap/techchallenge/oficina/integration/CustomerJwtIntegrationTest.java),
  método `pythonIssuerAuthenticatesOwnerAndHidesOtherOrders`, linha 90 nesta base,
  espera explicitamente **401** para CUSTOMER em `/api/v1/admin/customers`.
  Isso não satisfaz o aceite 403 solicitado. Não alterar a expectativa do smoke,
  substituir o token CUSTOMER por ADMIN ou declarar essa CI como prova de 403.

O teste usa ADMIN/customers e o smoke usa ADMIN/work-orders: ambos entram no mesmo
matcher `/api/v1/admin/**`. Confirmar a cadeia/configuração efetiva na frente Java,
registrar a causa e corrigir lá se necessário. Esta revisão não modifica Java,
configuração de confiança nem material criptográfico para fazer o teste passar.

## Transporte privado: HTTPS não surge do port-forward

[k8s/staging/api.yaml](../../k8s/staging/api.yaml) declara Service `ClusterIP`,
porta `http:8080`, e probes HTTP. Não há listener TLS Java ou terminação HTTPS
nesse manifesto. Um túnel SSM transportando essa porta continua apresentando
**HTTP** no socket local; usar `https://127.0.0.1:18080` não converte o servidor.
O smoke original da [Lambda #6](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/6)
exige HTTPS e portanto não é diretamente compatível com esse transporte.

A correção deve oferecer um modo separado, explicitamente restrito ao loopback
literal e à porta dedicada de uma sessão SSM autenticada, sem redirects, proxies
de ambiente, DNS arbitrário ou HTTP remoto. A sessão precisa ser conferida antes
de transmitir o JWT e encerrada ao final. Validar hostname/cadeia TLS continua
obrigatório para AWS/JWKS; assinatura RS256, `kid`, issuer, audience e tempos do
JWT continuam obrigatórios. Não existe exceção geral para HTTP de IP privado.
SSM protege o canal operador→nó; o trecho nó→pod ainda é o HTTP interno já existente.
Se o requisito exigir TLS até o processo Java, o adapter não satisfaz esse
requisito: registrar bloqueio e propor terminação TLS privada em outra frente.

Não abrir Gateway, Ingress, NodePort ou LoadBalancer e não usar `verify=False`
ou `curl -k`. A janela Lambda e as condições do adapter ficam no runbook da
[revisão Lambda](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/6),
com PR corretiva vinculada quando disponível. Não iniciar a janela enquanto
transporte, fixtures, igualdade das confianças e recuperação independente estiverem pendentes.

## Promoção por digest: publicação não comprova rollout

Auditoria de [release-local.sh](../../scripts/staging/release-local.sh):

1. O modo padrão é offline; usa digest fictício identificado somente para render.
2. `--execute` exige checkout limpo, sessão temporária, conta/região fixas, ECR
   imutável, tag `git-<SHA completo>` ausente, SSM Online e confirmação interativa.
3. Constrói por `git archive` do commit, publica em ECR, compara digest do push
   com `ecr describe-images` e renderiza `repo@sha256:...`.
4. Não chama `kubectl set image`, apply, rollout, SSM SendCommand ou migração.
   O YAML e o log temporários são apagados ao sair; não gera receipt persistente.

Logo, sucesso desse script comprova a publicação verificada e a renderização,
**não** a promoção no cluster. `SSM Online` também não comprova permissões para
comandos, sudo/RBAC, pull da imagem ou saúde do rollout. As bases Docker/pacotes
não fixados por digest impedem deduzir reprodutibilidade bit a bit do SHA Git.
Não executar novamente um push só para produzir evidência: tag existente bloqueia
a repetição, e a imagem já publicada deve ser consultada em modo somente leitura.

Na estação do operador, para auditar uma publicação existente sem publicar:

```bash
set -euo pipefail
set +x
export AWS_PAGER='' AWS_CLI_AUTO_PROMPT=off AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_SECURITY_TOKEN AWS_ENDPOINT_URL
aws_stage() { aws --profile oficina-staging --region us-east-2 "$@"; }
test "$(aws_stage sts get-caller-identity --query Account --output text)" = 797781632267
RELEASE_COMMIT=$(git rev-parse --verify HEAD)
# Usar o checkout/commit EXATO da publicação, não o HEAD documental por conveniência.
aws_stage ecr describe-images --repository-name oficina-staging-api \
  --image-ids "imageTag=git-$RELEASE_COMMIT" \
  --query 'imageDetails[].{digest:imageDigest,tags:imageTags,pushedAt:imagePushedAt}' \
  --output json
```

No nó K3s, dentro da sessão SSM já autenticada, estas consultas não mudam recursos:

```bash
sudo k3s kubectl -n oficina-staging get deployment oficina-api \
  -o jsonpath='{.metadata.generation}{" "}{.status.observedGeneration}{" "}{.spec.template.spec.containers[?(@.name=="api")].image}{"\n"}'
sudo k3s kubectl -n oficina-staging get pods -l app=oficina-api \
  -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.metadata.ownerReferences[0].name}{" "}{range .status.containerStatuses[?(@.name=="api")]}{.imageID}{" "}{.ready}{" "}{.restartCount}{end}{"\n"}{end}'
sudo k3s kubectl -n oficina-staging get replicasets -l app=oficina-api
sudo k3s kubectl -n oficina-staging get hpa
sudo k3s kubectl -n oficina-staging get service oficina-api \
  -o jsonpath='{.spec.type}{" "}{.spec.ports[*].port}{"\n"}'
```

Evidência mínima de promoção futura: commit e checks usados na aprovação,
digest anterior/novo no ECR, referência por digest do Deployment, revisão e
geração observada, `imageID` de **cada** pod, rollout, probes e HPA. Para manifesto
multiarch, comprovar o vínculo entre digest do índice e digest linux/amd64 efetivo;
não aceitar digest diferente sem essa prova. Registrar horário e identidade da
execução, sem valores de Secrets, bodies de OS, tokens ou CPFs. O snapshot atual
do cluster prova o digest observado, não quem/quando fez uma promoção histórica.

O [runbook de release](../staging/release-local.md) contém promoção/rollback
operacionais condicionados a aprovação. São uma etapa futura distinta; nenhum
dos comandos de mutação daquele runbook foi executado nesta revisão.

## CI e automação operacional

| Mecanismo | Evidência que produz | O que continua pendente |
| --- | --- | --- |
| [CI](../../.github/workflows/ci.yml) | Maven, build Docker local ao runner, validação Terraform sem backend, render/schema Kubernetes | Não publica ECR nem promove staging; usar checks do SHA real, não apenas descrição do PR. |
| [Kind Smoke Test](../../.github/workflows/kind-smoke.yml) | Instalação/health em cluster Kind descartável por `workflow_dispatch` | Não comprova TLS, fixtures, RDS ou autenticação no staging AWS. |
| `release-local.sh --dry-run` | Render offline com digest fictício | Não comprova build, publicação ou deploy. |
| `release-local.sh --execute` | Publicação manual com comparação push/ECR | Não é CD e não comprova rollout. Não autorizado nesta revisão. |
| SSM/runbooks | Procedimento operacional privado, sujeito a evidências e autorização próprias | A existência do script não prova sua execução nem seus resultados. |

Não há trabalho de OIDC nesta frente. A observabilidade da
[#13](https://github.com/ppdssdev/tech-challenge-oficina/pull/13) deve ser descrita
como implementação proposta até haver deploy/evidência próprios; nunca como
instalada só porque os manifestos ou checks existem.

## Integração #13 → #14 sem substituir README

Ordem planejada: revisar/integrar #13 antes de #14; a mudança de estado depende
de revisão humana e não é executada aqui. A consulta das refs nesta revisão
encontrou #13 em `fadc2b9eabac5148e449a4554ea1a1e09e699377` e #14 em
`ac5834624873219fc441c6cc54056dfb14baee71`, ambas baseadas em `main`.

O comando local `git merge-tree --write-tree HEAD origin/feat/staging-observability`
detectou **conflito de conteúdo em README.md**. Nenhuma resolução foi aplicada
ao README. Para preparar integração após #13, partir do HEAD documental atualizado,
integrar o novo main sem reescrever histórico e resolver o trecho conflitante
preservando o texto auditado de #14 **e** o link/runbook de observabilidade de #13.
Não escolher README inteiro com `--ours`/`--theirs`. Repetir o verificador de links,
revisar semanticamente datas/estado observado e só então atualizar a PR documental.

Links entre repositórios continuam dirigidos às PRs/branches enquanto os documentos
não estiverem em main. A sequência Lambda é [#6](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/6)
antes de [#7](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/7).
Complementos: [K3s #6](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/pull/6)
e [RDS #8](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/pull/8).
Não apagar relatórios anteriores; registrar o snapshot novo como adendo datado.

## Validação local desta revisão

```bash
python3 -m unittest discover -s tests/staging -v
python3 scripts/staging/test-render.py
bash scripts/staging/release-local.sh --dry-run
bash -n scripts/staging/release-local.sh
shellcheck scripts/staging/release-local.sh scripts/staging/render.sh
git diff --check
```

Resultado: 11 testes de release passaram; renderer, dry-run, Bash e ShellCheck
passaram. Kubectl foi usado somente para Kustomize local; AWS e Docker são mocks
nos testes. Nenhuma suíte Java foi reexecutada: seus fontes foram auditados para
identificar o bloqueio de contrato, sem alterar código/testes ou inferir o
resultado do staging. Esta documentação adicional não muda esses resultados.
