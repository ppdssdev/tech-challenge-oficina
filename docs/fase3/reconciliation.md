# Reconciliação da Fase 3

Base inspecionada: `origin/main` e `HEAD` em
`a72246bfab2072d3cfbd66b61342a774f3905d2a`.
Commit recuperado: `9253c01fc7c85be1f5b3d35c4a3f888027ad4f89`.
As versões base, ours e theirs dos sete conflitos foram comparadas. O cherry-pick
permanece aberto; não houve commit, push, deploy ou alteração de outros repositórios.

Os arquivos de trabalho estão reconciliados e sem marcadores. **O staging está
pendente:** `git add` foi tentado e bloqueado por `.git` somente leitura
(`Unable to create .git/index.lock: Read-only file system`). Por isso, `git status`
ainda lista os sete conflitos no índice e a V8 recuperada ainda está no índice
antigo, embora já tenha sido substituída por V11 nos arquivos de trabalho.

A lista exata dos arquivos revisados está em `/tmp/fase3-reconcile/stage-paths.txt`.
Executar fora do sandbox, na raiz deste repositório:

```sh
git add --pathspec-from-file=/tmp/fase3-reconcile/stage-paths.txt
git diff --cached --check
git diff --check
git ls-files -u
```

A última saída deve ficar vazia. A lista inclui a remoção da V8 conflitante e a
adição de V11, os ajustes de integração e este relatório; não inclui os scripts
não rastreados de carga. Isso prepara o índice sem continuar o cherry-pick.

## Decisões de integração

| Área | Resultado |
| --- | --- |
| Consulta por código | POST `/api/v1/public/work-orders/{code}/status`, documento no JSON, como na main. Autenticação e ownership recuperados da Fase 3. |
| Aprovação/recusa | ADMIN ou CUSTOMER proprietário; código/CPF informado não substitui o UUID autenticado. Testes de idempotência, concorrência e histórico mantidos. |
| GET legado | Somente perfil local, com a mesma autorização de método. Fora desse perfil não há controller GET. |
| ADMIN | Login e HMAC mantidos, com algoritmos HS256/384/512, claims administrativos e papel persistido ADMIN. Token HMAC de usuário CUSTOMER é inválido (401); RS256 nunca promove para ADMIN. |
| Cliente por UUID | Cadeia `/api/v1/customer/**` da main preservada: JWKS, principal `CustomerIdentity`, ownership no caso de uso e 404 para recurso alheio/inexistente. |
| Cliente por código | Validador PEM recuperado (`app.jwt.customer`), RS256 e `ROLE_CUSTOMER`; ownership de código usa UUID do principal. Recurso alheio/inexistente resulta em 403. |
| Filtros | Cadeias ordenadas, filtro legado sem registro global no servlet, logs sanitizados e negação das demais rotas. Falha de um validador não tenta outro. |
| Perfis | Local requer ativação explícita; credenciais demonstrativas ficam nesse perfil. Staging mantém TLS verify-full, runtime sem Flyway/DDL, Job separado e credenciais por finalidade. |
| Histórico em staging | Grant SELECT/INSERT para `work_order_status_history`, necessário aos fluxos da main. Sem UPDATE/DELETE nessa tabela. |

## Migrations e contratos SQL

Confirmado por `git ls-tree origin/main src/main/resources/db/migration/`:
V8 `add_customer_active`, V9 `create_work_order_status_history` e V10
`customer_identity_view` já existem na main. V11 é a próxima versão disponível.
V1–V10 permanecem byte a byte iguais à main, sem mudança de checksum.

A V8 do commit recuperado foi integrada como
`V11__create_explicit_customer_identity_view.sql`. Ela reutiliza `auth`, cria
`auth.customer_access` e `auth.customer_identity_v2`; não substitui a view v1.

| Contrato | CPF | `active` | Leitura |
| --- | --- | --- | --- |
| v1, main | Normalizado; duplicatas permanecem para detecção pelo consumidor | `customers.active` | `auth_readonly`, bootstrap original preservado |
| v2, recuperado | Somente CPF canônico; nenhuma identidade em caso de ambiguidade | `coalesce(customer_access.active, false)` | `oficina_auth_reader`, bootstrap estrito |

Na v2, um documento CPF legado que normalize para o mesmo número de outra linha
oculta a identidade canônica, mesmo se habilitada. A normalização é usada apenas
para detectar colisões: a migration não corrige documentos nem escolhe uma das
identidades. CNPJ e valores não canônicos não são publicados. Dígitos verificadores
continuam validados pela API e pela Lambda, não pelo SQL. O índice de expressão
é não único para permitir upgrade sem apagar dados ambíguos.

Atividade de negócio e habilitação v2 permanecem independentes, como aprovado na
reconciliação. `customers.active=false` não revoga `customer_access.active=true`.
Para suspender emissão v2, inativar a habilitação explícita. Tokens já emitidos
mantêm sua validade até expiração; não foi introduzida revogação por requisição.

O bootstrap estrito concede somente USAGE em auth e SELECT em v2. O login
`oficina_lambda` herda somente `oficina_auth_reader`, sem acesso a v1, tabelas-base,
Flyway, DDL ou papel migrator. O bootstrap original de v1 continua disponível para
consumidores antigos. Não conceder v1 à role estrita como contorno de incompatibilidade.

Os testes SQL recuperados cobrem upgrades V7→V11 e V10→V11, elegibilidade explícita,
ambiguidade e privilégios. O teste existente V9→latest mantém as expectativas de v1.
O teste de histórico em schema isolado termina em V9: V10/V11 usam objetos globais
em auth/public e são cobertas pelos testes em bancos isolados.

## Lambda: alterações obrigatórias antes de deploy

**Deploy não liberado.** A leitura do checkout irmão confirmou que a Lambda atual
ainda usa v1. Nenhum arquivo desse repositório foi alterado. Compatibilidade de
colunas não comprova consumo do contrato estrito. O bootstrap v2 recusa acesso à
consulta antiga, em vez de conceder silenciosamente a semântica v1.

Mudanças necessárias no repositório `tech-challenge-oficina-auth-lambda`:

1. Em `auth/postgres.py`, trocar apenas o nome da view na consulta parametrizada:

   ```python
   cursor.execute(
       'SELECT customer_id, active FROM auth.customer_identity_v2 '
       'WHERE cpf = %s LIMIT 2', (cpf,),
   )
   ```

   Manter os timeouts, transação de leitura e `LIMIT 2`. Não adicionar fallback
   para v1. Os tipos UUID/boolean e o formato de `Identity` são compatíveis.
   `auth/service.py` já exige exatamente uma linha e `active is True`; preservá-lo.
   `auth/request.py` já aceita somente 11 dígitos ASCII com verificadores válidos,
   rejeitando CPF formatado; não normalizar entradas inválidas no consumidor.
   `auth/cloud.py` já restringe a emissão à lista de UUIDs sintéticos; preservar.

2. Em `scripts/prepare_integration.py`, atualizar o contrato revisado de
   `list(range(1, 9))` para `list(range(1, 12))` e o último arquivo de
   `V8__create_customer_identity_view.sql` para
   `V11__create_explicit_customer_identity_view.sql`. Atualizar mensagens/docstring
   para V1–V11. Manter recusa de banco remoto/não vazio, leitura do SQL real Java,
   execução das migrations como migrator e bootstrap como administrador.

3. Em `tests/test_prepare_integration.py`, atualizar a expectativa da mensagem
   `Java V1–V8` para `Java V1–V11` e testar rejeição de contrato incompleto/futuro.
   Em `tests/test_postgres.py`, trocar as referências SQL, regclass e auditorias de
   privilégio de v1 para v2; renomear as descrições V8 para V11. Manter todos os
   cenários de habilitação explícita, ambiguidade, TLS e restrição sintética, e
   acrescentar negação de `SELECT * FROM auth.customer_identity_v1` ao login estrito.
   A auditoria de privilégios efetivos deve excluir somente v2, não as duas views.

4. Atualizar README e documentos operacionais `docs/prompt12-integration.md`,
   `docs/prompt13-security-integration.md` e `docs/infra-staging.md`: V1–V11, consumo
   v2 e distinção da atividade de negócio. A CI exige `JAVA_CONTRACT_SHA`; apontá-la
   ao futuro commit Java reconciliado e revisado, após publicação autorizada.
   Não usar o SHA original recuperado nem uma branch móvel.

5. Executar os testes Python com PostgreSQL descartável/TLS e **zero skips**, usando
   o login real `oficina_lambda`. A ausência de TEST_DATABASE_URL pula os testes SQL
   e não serve como aceite. O teste deve atravessar o adapter real e o handler:
   sem habilitação → 401; habilitado → token; inativo/ambíguo/excluído → 401;
   UUID fora da allowlist sintética → 401. Acesso direto a v1/tabelas-base deve falhar.

O aceite deve registrar o SHA/artefato da Lambda contendo a query v2, o SHA Java,
a aplicação de V11, os grants efetivos e os resultados dos testes. Validar também
o artefato empacotado que será publicado, não apenas o checkout. Só então liberar
o deploy coordenado. Manter v1 disponível aos consumidores antigos com suas roles
próprias; nenhum consumidor deve receber o papel migrator ou uma role de leitura ampla.

## RS256, JWKS e staging

O emissor real em `auth/jwt.py` usa RS256, RSA ≥ 2048 bits, `kid`, issuer, audience,
`sub` UUID canônico, `role=CUSTOMER`, `iat=nbf` e `exp=iat+900`. Esse formato atende
aos dois validadores Java. Há duas configurações explícitas, sem fallback:

| Valor do emissor Lambda | Cadeia JWKS `/customer/**` | Caminho PEM por código |
| --- | --- | --- |
| `JWT_ISSUER` | `CUSTOMER_JWT_ISSUER` | `app.jwt.customer.issuer` |
| `JWT_AUDIENCE` | `CUSTOMER_JWT_AUDIENCE` | `app.jwt.customer.audience` |
| `JWT_KID` / chave pública | `kid`, `n`, `e` correspondentes no JWKS HTTPS em `CUSTOMER_JWKS_URI` | entrada do mesmo kid em `app.jwt.customer.public-keys` |

Para um token operar nas duas famílias de rotas, ambos os validadores precisam
confiar na mesma chave pública, issuer e audience. PEM não configura JWKS
automaticamente. A fixture `contracts/jwks.public.json` do irmão não comprova
publicação de um JWKS de staging com a chave real.

O manifest staging agora exige `jwks-uri`, `issuer`, `audience` no ConfigMap
`oficina-customer-jwt`, além do YAML/PEMs. São parâmetros públicos, não a chave
privada. `app.jwt.customer.enabled=false` desliga somente PEM; JWKS continua
obrigatório fora dos perfis locais. Preservados cache JWKS limitado, timeout,
ausência de redirecionamento e falha segura em indisponibilidade. PEM usa skew
de 30s; JWKS usa até 60s e cache até 300s, conforme a main.

Antes do deploy, disponibilizar o JWKS HTTPS confiável e testar da rede da API:
token real do handler v2 acessa somente a própria OS; outro cliente não acessa;
ADMIN é recusado ao cliente; login HMAC e fluxo administrativo completo funcionam;
rotação permite a nova chave e invalida a removida conforme cache/rollout. A rotação
de PEM exige rollout, enquanto JWKS atualiza dentro das regras de cache. Não há
evidência de endpoint/chaves reais configurados neste trabalho; isso continua pendente.

## Verificação local e reprodução externa

`mvn -B clean verify` foi tentado. Compilação passou; os testes com Testcontainers
falharam por falta de acesso ao Docker (`/var/run/docker.sock: permission denied`).
Não foi possível validar PostgreSQL, grants ou TLS ponta a ponta neste sandbox.

A execução final sem Docker passou: **121 testes, zero falhas/erros/skips**.
Os testes sem Docker incluem as regressões MVC novas com filtros reais, HMAC/PEM,
ownership de consulta/aprovação/recusa, isolamento da cadeia JWKS e principal tipado.
A seleção parcial não atende sozinha ao limite JaCoCo de `domain.model.user`,
normalmente exercitado pela integração. O gate de cobertura não foi reduzido.

Comando para essa seleção:

```sh
mvn -B '-Dtest=!**/*IntegrationTest' test
```

Comandos externos para completar a validação, em ambiente com Docker, Java 21,
OpenSSL e as dependências Python previstas pela CI:

```sh
mvn -B clean verify
kubectl kustomize k8s/local > /tmp/oficina-local.yaml
kubectl kustomize k8s/staging > /tmp/oficina-staging.yaml
git diff --check
git diff --cached --check
```

Os dois overlays foram renderizados localmente com o binário kubectl instalado;
nenhum manifest foi aplicado. Relatórios antigos nos documentos recuperados foram
identificados como evidência histórica e não certificam esta reconciliação.
