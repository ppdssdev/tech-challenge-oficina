# Evidências da integração — 2026-11-03

Base main: `97f6cb68df5cd8f062e7e9db5fb412723f5c652e` (#13 integrada).
PR #15 atualizada por merge normal: `95f37166ae5957e20db53a7c97e88cd0dc44d662`.
PR #16 atualizada por merge normal: `13ac82d5a4d9db3343d0ec97fb0a17a8cfb46ce7`.
Não houve conflitos textuais. Nenhum force push, merge de PR ou deploy.

CI dos SHAs atualizados: [#15 aprovado](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41691542445),
[#16 aprovado](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41691543166).

Worktree combinado `/tmp/oficina-combined`, branch `validate/main-pr15-pr16`,
commit `da78d5e95eb9875ba29fc82543d8c91d049ec8ed` (temporário, não publicado).

```bash
CUSTOMER_TEST_PYTHON=/tmp/oficina-observability-python/bin/python mvn -B clean verify -Dlambda.repo=/tmp/oficina-auth-demo -Dlambda.python=/tmp/oficina-observability-python/bin/python
docker build --platform linux/amd64 -t oficina-api:combined-main-pr15-pr16 .
```

Resultado: **229 testes, zero falhas, zero erros, zero skips**, BUILD SUCCESS.
`realPythonSignerAuthenticatesAsCustomer` executado com checkout Lambda
`1ff247717e35054967495ba7684742c67aaeb9da`. O checkout tinha alterações locais
em documentação/testes; o assinador utilizado não estava modificado. Nada foi
escrito nesse checkout. Testcontainers usou PostgreSQL local descartável.

JaCoCo: 1984/2273 linhas (87,29%), 519/742 branches (69,95%); todos os limites
de cobertura de domínio >=80% aprovados. Docker build linux/amd64 aprovado;
image ID local `sha256:6e30b357c1852fe25b7334103eb8572af76df846e7988e1c6e80f3ebb2398bb3`.
Esse ID local não é digest publicado no ECR e não deve ser usado para promoção.

Artefatos locais e SHA-256:

| Arquivo | SHA-256 |
|---|---|
| `/tmp/oficina-combined-verify.log` | `b6af1b52e1703450e4792adf8517ae27c15d8144878019e6eca6ab905b764b4d` |
| `/tmp/oficina-combined-docker.log` | `a44e07975a5e210d35d0757a5bb77f8f02184be2cdf9bd05e59b0b5946d475dc` |
| `/tmp/oficina-combined/target/site/jacoco/jacoco.xml` | `9439edf51a2f33b66bbec954701b030d7d0fb52aa46343f185257b04fc874784` |

Revisão final: diff combinado contra main vazio para README geral, manifests,
scripts e documentação de observabilidade da sessão 1. PR de promoção limitada
a runbook, configuração opt-in, evidências e testes offline. Testes do runbook
exercitam promoção/rollback, ausência e valueFrom anteriores, preservação de
outras variáveis, rejeição de concorrência e digest efetivo divergente.
Validados também release dry-run e renderizador staging existentes.

O teste HTTP local correlaciona JSON e OTLP; não comprova Tempo staging.
Aceite remoto continua pendente: confirmação da sessão 1, promoção manual,
JSON/trace Tempo da mesma requisição real e métricas de negócio finitas.
Procedimento, comandos por ambiente e ordem de merges/revalidação:
[promotion-manual.md](promotion-manual.md). Nenhuma exportação foi ativada.
