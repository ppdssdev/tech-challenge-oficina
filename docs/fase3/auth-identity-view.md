# Identidade CPF — implementação Prompt 04

V10 adiciona `auth.customer_identity_v1(customer_id UUID, cpf text, active boolean)`
sobre `public.customers` (schema de domínio contratado). V1–V9 intactas. Não altera dados,
status, histórico ou login administrativo. CNPJ e documentos não normalizáveis são excluídos.
A view normaliza pontuação/espaços; dígitos verificadores são responsabilidade do emissor.
O índice único existente cobre o documento original, não sua normalização. Duplicidades
normalizadas são preservadas e devem causar recusa: SELECT parametrizado com LIMIT 2,
sem filtrar active antes de contar. Não adicionar DISTINCT ou escolher primeiro resultado.

## Provisionamento de permissões

Após Flyway, executar `psql -v ON_ERROR_STOP=1 -f scripts/provision-auth-readonly.sql`
como administrador autorizado. O script idempotente cria a role de capacidade NOLOGIN
`auth_readonly`, concede somente USAGE no schema auth e SELECT na view. Migration não
exige role preexistente. A view usa permissões do dono; não usar security_invoker.
O dono deve ser migrador confiável; Lambda não pode ser dono de objetos.

Provisionar separadamente um LOGIN dedicado por ambiente com senha gerenciada ou IAM
na tarefa AWS futura, NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOREPLICATION, INHERIT e
membership exclusivamente em auth_readonly. Configurar default_transaction_read_only
no LOGIN também (parâmetros de role não são herdados), além do controle no adapter.
Não passar senhas na linha de comando ou commitar SQL preenchido. Conceder CONNECT
somente no banco necessário. Auditar memberships e permissões PUBLIC: PostgreSQL soma
privilégios; retirar grants herdados sobre tabelas de domínio antes de liberar o LOGIN.
Não conceder USAGE no schema public, SELECT em tabelas ou permissões default amplas.
Se auth_readonly preexistir, auditar atributos/memberships antes de reutilizá-la.

`CustomerIdentityViewIntegrationTest` cria PostgreSQL descartável, migra até V9,
insere dados sintéticos, migra V10 e verifica preservação, ativos/inativos/ausentes,
CNPJ, duplicidade normalizada, tipos e acesso negado a tabelas/escritas/DDL com a role.
Executar `mvn -B verify` com Docker disponível.

## Gate Spring/AWS permanece aberto

A aplicação ainda emite/valida HMAC; os tokens RS256 CUSTOMER da Lambda **não são
aceitos** por esta mudança. Preservado login ADMIN. Próxima etapa: validador RS256
com JWKS confiável por ambiente, issuer/audience/tempos/kid, principal UUID e mapeamento
explícito de role CUSTOMER. Provar 403 em todas as rotas ADMIN e isolamento de dados
entre clientes. Testar rejeição de none, HMAC indevido, assinatura adulterada, kid
inesperado, tokens expirados e rotação; preservar ADMIN por migração testada, nunca
selecionando confiança apenas pelo alg do token. Fixture pública no repo auth em
`contracts/jwks.public.json`; privada correspondente descartada, não serve para emissão.

Sem deploy, recursos AWS, alteração K3s/RDS ou merge nesta tarefa.
