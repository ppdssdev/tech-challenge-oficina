# Fase 3 — Security hardening

## Configuração segura

Em qualquer ambiente que não use explicitamente os profiles `local`, `test` ou `it`, a aplicação exige `APP_JWT_SECRET`, `APP_ADMIN_DEFAULT_USERNAME` e `APP_ADMIN_DEFAULT_PASSWORD`. O startup rejeita segredo JWT com menos de 32 caracteres, valores demonstrativos conhecidos e senha administrativa com menos de 12 caracteres ou conhecida como fraca. Os valores nunca são escritos em logs.

O profile `local` é uma opção consciente para desenvolvimento e demonstração:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Não habilite `local` em produção. Injete segredos por mecanismo próprio da plataforma e rotacione-os fora do repositório.

## RBAC

`/api/v1/admin/**` exige a autoridade `ROLE_ADMIN`. Requisição anônima recebe `401`; usuário autenticado com outra role, inclusive futura `ROLE_CUSTOMER`, recebe `403`. Rotas públicas e de autenticação permanecem públicas.

## Mudança REST da consulta pública

O contrato seguro passa a ser:

```http
POST /api/v1/public/work-orders/{code}/status
Content-Type: application/json

{"document":"52998224725"}
```

Isso evita CPF/CNPJ em URLs, históricos, métricas e access logs de proxies. O antigo `GET /{code}/status?document=...` fica disponível somente com o profile `local`, aparece como deprecated no OpenAPI local e não é registrado em produção. Consumidores devem migrar para o POST antes do deploy desta versão; fora de `local`, o GET responde `405 Method Not Allowed`.

Mensagens registradas pelo tratamento de exceções mascaram sequências com formato de CPF/CNPJ. Ainda assim, a política operacional deve impedir logging de corpos HTTP e aplicar mascaramento também em proxy, ingress e APM.

## Critérios de aceite

- produção não inicia sem variáveis sensíveis ou com valores conhecidos/fracos;
- segredo e senha não aparecem em logs;
- admin autenticado acessa `/api/v1/admin/**`;
- anônimo recebe `401` e usuário não admin recebe `403`;
- consulta pública usa JSON e não coloca documento na query string;
- GET legado existe somente em `local` e está deprecated;
- migrations, domínio de OS e regras de estoque permanecem inalterados.
