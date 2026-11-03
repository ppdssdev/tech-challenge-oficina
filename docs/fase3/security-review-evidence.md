# Snapshot da revisão de segurança — 2026-11-03

Evidência adicional, sem substituir auditorias históricas. [Dados GitHub](security-github-baseline.json)
e [dados AWS sanitizados](security-aws-snapshot.json) preservam horários e resultados.
Os SHAs abaixo são as bases consultadas **antes dos novos adendos documentais**;
checks devem ser reconsultados nos novos heads. Nenhum resultado foi inferido do corpo das PRs.

## GitHub: revisões e checks reais

Consulta iniciada em `2026-11-03T16:58:49.037269+00:00`.

| PR | SHA consultado | Estado | Check runs |
|---|---|---|---|
| [tech-challenge-oficina #13](https://github.com/ppdssdev/tech-challenge-oficina/pull/13) | `fadc2b9eabac` | open; base `main` | [Docker build](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41642527647/job/125940201534): success; [Infrastructure validation](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41642527647/job/125939382292): success; [validate](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41642528045/job/125939382100): success; [Maven verification](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41642527647/job/125939381694): success |
| [tech-challenge-oficina #14](https://github.com/ppdssdev/tech-challenge-oficina/pull/14) | `ac5834624873` | open; base `main` | [Docker build](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41644735611/job/125947648630): success; [Maven verification](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41644735611/job/125947003925): success; [Infrastructure validation](https://github.com/ppdssdev/tech-challenge-oficina/actions/runs/41644735611/job/125947003627): success |
| [tech-challenge-oficina-auth-lambda #6](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/6) | `6736f7f2ed3d` | open; base `main` | [Auth contract and build](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/actions/runs/41642968661/job/125940870009): success |
| [tech-challenge-oficina-auth-lambda #7](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/7) | `5ee81f4250b4` | open; base `main` | [Auth contract and build](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/actions/runs/41644756074/job/125947075285): success |
| [tech-challenge-oficina-infra-k3s #6](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/pull/6) | `e1994e2275f4` | open; base `main` | [Terraform validation and security](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/actions/runs/41644767297/job/125947116834): success |
| [tech-challenge-oficina-infra-rds #8](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/pull/8) | `ab627ed206a0` | open; base `main` | [Terraform validation and security](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/actions/runs/41644781675/job/125947165713): success |

O endpoint de status legado retornou `pending` com zero statuses nos seis heads;
isso não é check falhando ou em execução. Os check-runs listados estavam todos
`completed/success`. A nova [Lambda #8](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/8)
é corretiva, draft e empilhada sobre #6. O workflow da Lambda só executa para
PRs em main/push main; ausência de checks nesta base não equivale a CI aprovada.
Exigir Auth CI no SHA corrigido ao integrar #6 e, depois, #7.

`soat-architecture`: acesso efetivo **write na API; read na Lambda, K3s e RDS**.
Zero convites pendentes para esse usuário nos quatro repositórios, consultados pela
API de invitations. As listas cabiam na primeira página (limite 100); permissão
foi consultada separadamente. Nenhum convite ou acesso foi alterado. A afirmação
anterior “banca somente na API” é histórica e foi superada por esta leitura.

## AWS e cluster: leitura real

Consulta agregada em `2026-11-03T17:02:14.100091+00:00`, perfil `oficina-staging`, conta
`797781632267`, região `us-east-2`. Lambda `oficina-staging-auth`: reserved
concurrency **0**, Active, Python 3.13, atualização Successful e referências de
assinatura presentes. Gateway `t6p562hkc9`: execute-api desabilitado e única rota
`POST /auth/token` com `AWS_IAM`. Não houve Invoke ou escrita de concorrência.

RDS `oficina-staging-postgres`: available, PostgreSQL 16.15, privado, armazenamento
criptografado e CA `rds-ca-rsa2048-g1`. Isso não prova dados/grants ou TLS de uma
conexão SQL. Nenhuma consulta SQL foi executada no RDS.

SSM informou nó `i-0ab373b1ba5c14b3e` Online. O comando
`fab6325c-8be2-4174-9c20-078181a5b157` executou **somente kubectl get**, com sucesso:
Deployment 3/3, três pods Ready, Service oficina-api ClusterIP 8080→http, nenhum
Ingress no namespace. Deployment e imageID dos três pods coincidem em:

```text
797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api@sha256:40b333a1546a157438789945b6bc47d7eea5c59e8958ba9dd31176beb8686527
```

Esse snapshot comprova o digest em execução naquele instante; não comprova sua
promoção histórica pelo release-local.sh, nem associa automaticamente o digest a
um SHA Git. Não foi executado rollout, port-forward, smoke autenticado ou deploy.

## Entrega e limites

- [Adendo API](security-integration-review.md): causas Java, auditoria de promoção,
  comandos e roteiro preliminar; 11 testes release, render/dry-run e ShellCheck passaram.
- [Correção Lambda #8](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/pull/8):
  adapter Java estrito em loopback, RDS verify-full e cleanup limitado;
  92 testes smoke; 178 testes Python passed / 22 PostgreSQL skipped. Não declarar
  execução dos 22; CI futura no SHA final continua necessária.
- Adendos Lambda #7, K3s #6 e RDS #8 preservam README/histórico. Inventários SQL
  reader/auditor passaram em PostgreSQL 16 descartável sem rede/portas; dados
  sintéticos reais, proveniência e allowlist continuam não comprovados.
- API #13→#14 e Lambda #6→#7 têm conflitos reais em README.md na simulação
  merge-tree. Resolver editorialmente depois, preservando as duas frentes.

Aceites permanecem **200/401/403/404**. Sem merge, deploy, habilitação Lambda,
mutação do banco/IAM/Terraform/acessos ou trabalho OIDC. TLS até o próprio listener
Java permanece ausente; o adapter privado não transforma HTTP em HTTPS fim a fim.
