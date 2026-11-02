# Contrato SQL Java / Lambda — etapa 6

## Fontes e escopo

A reconciliação preserva V1–V10 da main, incluindo `auth.customer_identity_v1`
(atividade de negócio e CPF normalizado), e recupera a habilitação explícita da
Fase 3 como `auth.customer_identity_v2`, criada pela V11. A V8 original recuperada
foi renumerada para não colidir com `V8__add_customer_active.sql`.

A consulta do consumidor que optar pelo contrato estrito v2 deve ser:

```sql
SELECT customer_id, active
FROM auth.customer_identity_v2
WHERE cpf = %s
LIMIT 2;
```

A Lambda exige exatamente uma linha e `active is True`. Valida CPF com 11 dígitos
ASCII e dígitos verificadores antes de consultar. A API também valida os dígitos
verificadores e persiste o documento sem formatação. O UUID retornado é
`public.customers.id`, nunca `app_users.id`.

## Decisão explícita sobre active

A main já possui `customers.active` e operação administrativa de inativação.
Esse comportamento e a view v1 permanecem intactos. A v2 usa uma habilitação
independente, recuperando a semântica do commit original sem mudar consumidores v1.
Na v2, `active` depende exclusivamente de `auth.customer_access`; não combina
implicitamente atividade de negócio com habilitação de autenticação.

V11 cria `auth.customer_access(customer_id UUID PRIMARY KEY, active BOOLEAN NOT NULL
DEFAULT FALSE)`. É uma habilitação **exclusiva de autenticação pela Lambda**, não um
novo estado de negócio de Customer. A FK referencia `customers.id` e tem exclusão
em cascata. Nenhuma linha é criada ou habilitada automaticamente.

- Sem registro de habilitação: view retorna `active = false`.
- Registro com `false`: identidade inativa para autenticação; Lambda deve recusar.
- Registro com `true`: identidade habilitada, sujeita ao filtro de CPF da view.
- Cliente excluído: não há identidade nem habilitação remanescente.

**Efeito operacional deliberado:** clientes existentes e novos permanecem sem
login Lambda até habilitação explícita por operador autorizado. O CRUD da API,
o domínio, DTOs e login ADMIN HMAC não mudam. Alterar o CPF de um cliente mantém
sua habilitação, pois ela acompanha o UUID. Não existe endpoint de habilitação
nesta etapa; o procedimento operacional precisa de revisão antes do uso real.
Inativação impede novas emissões; não revoga tokens já emitidos.

## View e unicidade

`auth.customer_identity_v2` expõe somente `customer_id` (UUID), `cpf` (documento
textual) e `active` (booleano não nulo por expressão).

- Somente `document_type = 'CPF'` e formato canônico de 11 dígitos ASCII entram.
- CNPJ, documentos formatados, curtos ou com letras são excluídos.
- Não há normalização silenciosa nem reescrita de dados legados na migration.
- A unicidade exata já existente de `customers.document_number`, junto à PK da
  tabela de habilitação, impede múltiplas linhas para o mesmo CPF canônico.
- Se outra linha CPF normalizar para o mesmo número (legado formatado), a identidade
  canônica também é ocultada. A ambiguidade falha fechada, sem escolher um cliente.
- Um índice parcial de expressão acelera essa checagem; não é UNIQUE, para permitir
  upgrade sobre dados legados ambíguos sem exigir limpeza destrutiva.
- A view não duplica o algoritmo de dígitos verificadores: ele continua na API e na
  Lambda. Uma escrita SQL administrativa que burle a API pode criar CPF canônico
  inválido; a Lambda o rejeita antes da consulta. Formato não prova titularidade.

A view usa `security_barrier` e permissões do proprietário, permitindo a consulta
sem conceder SELECT nas tabelas-base. A migration assume o schema `public`, já usado
pelo projeto. Reutiliza o schema `auth` da V10 e cria tabela/view novas; objetos
conflitantes falham para revisão. V1–V10 e seus checksums não foram alterados.

## Bootstrap PostgreSQL

`scripts/bootstrap-auth-reader.sql` é separado do Flyway: requer o proprietário da
migration com CREATEROLE e deve ser executado uma vez, após V11, com:

```sh
psql -v ON_ERROR_STOP=1 -f scripts/bootstrap-auth-reader.sql
```

A conexão deve ser fornecida pelo ambiente seguro do operador. O arquivo não
contém senha, LOGIN, credencial AWS ou provisionamento de banco. Cria a role de
capacidade `oficina_auth_reader` com NOLOGIN e sem privilégios administrativos,
concedendo apenas USAGE em `auth` e SELECT na view. Não usa ALL TABLES nem DEFAULT
PRIVILEGES. Role preexistente provoca erro e rollback, sem confiar em grants antigos.

PostgreSQL não possui DENY que neutralize PUBLIC. O script aborta atomicamente se
essa role herdar acesso a tabelas/colunas de aplicação ou CREATE em schemas de
usuário via PUBLIC. Não revoga privilégios globais de outras aplicações. O operador
deve resolver grants inseguros e repetir o bootstrap; futuros grants também exigem
revisão. Catálogos do PostgreSQL e privilégios padrão de conexão/TEMP não são tabelas
de aplicação. Funções SECURITY DEFINER ou extensões privilegiadas adicionadas no
futuro precisam de auditoria própria; o script não é um auditor geral do cluster.

O LOGIN exclusivo da Lambda e seu mecanismo de credenciais ficam para provisionamento
separado. Ele deverá herdar apenas `oficina_auth_reader`, sem propriedade de objetos
ou memberships adicionais. Nenhum LOGIN real foi criado nesta etapa. Os testes
criam LOGIN e senha aleatória apenas no PostgreSQL descartável do Testcontainers.
Roles PostgreSQL não têm relação com `ADMIN`/`CUSTOMER` nem com `app_users`.

Exemplo de habilitação por operador autorizado, com UUID passado por variável psql,
sem dados de cliente versionados:

```sql
INSERT INTO auth.customer_access(customer_id, active)
VALUES (:'customer_id'::uuid, true)
ON CONFLICT (customer_id) DO UPDATE SET active = EXCLUDED.active;
-- Para inativar, use false. A role de leitura não pode executar esta operação.
```

## Testes e pendências

`CustomerIdentitySqlIntegrationTest` usa PostgreSQL 16/Testcontainers e Flyway reais:
consulta parametrizada equivalente à Lambda, tipos UUID/boolean, habilitação e
inativação, exclusão, CPF duplicado/ambíguo, documento de outro tipo, entrada de
injeção SQL, upgrade de V7 com dados e reexecução sem migrations pendentes.
O bootstrap é executado do próprio arquivo; conexão LOGIN real valida SELECT na
view, negação de tabelas-base, DML/DDL e SET ROLE do proprietário. Também testa
rollback do bootstrap diante de um grant inseguro para PUBLIC.

Consumidores atuais da Lambda continuam usando v1 e `scripts/provision-auth-readonly.sql`.
O contrato estrito v2 usa `scripts/bootstrap-auth-reader.sql` e a role
`oficina_auth_reader`, sem SELECT na v1. A adoção de v2 exige configurar a consulta
no consumidor e habilitar explicitamente seus clientes. Este trabalho não altera
outros repositórios nem presume que uma Lambda existente já use v2.

Os testes cobrem upgrade V7→V11 (quatro migrations) e, pela suíte existente,
V9→V11 preservando o comportamento de v1. A validação desta reconciliação e as
limitações do ambiente estão no [relatório](../fase3/reconciliation.md).

## Evidência histórica do commit recuperado (antes da reconciliação)

- `mvn -B clean verify`: BUILD SUCCESS, 103 testes, zero falhas/erros/ignorados.
- Seis novos testes SQL aprovados, além dos três testes de migrations existentes.
- Regressões de autorização do Prompt 5 e login HMAC aprovadas na suíte completa.
- Limites de cobertura JaCoCo aprovados; `git diff --check` sem problemas.
- A primeira execução direcionada identificou uma expectativa de SQLSTATE incorreta:
  DML na view com JOIN retorna 55000 (view não atualizável). O teste final verifica
  essa recusa e, separadamente, a ausência de privilégios DML; tabelas/DDL retornam
  42501 (permissão negada).
- Não executados: suíte Python do irmão, integração RS256 ou testes em AWS.
