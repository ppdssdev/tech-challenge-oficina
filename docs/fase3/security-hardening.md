# Fase 3 — Security hardening

## Configuração segura

Sem profile ou em ambientes produtivos, a aplicação exige `APP_JWT_SECRET`, `APP_ADMIN_DEFAULT_USERNAME`, `APP_ADMIN_DEFAULT_PASSWORD` e `SPRING_DATASOURCE_PASSWORD`. JWT exige pelo menos 32 bytes UTF-8; senha administrativa exige 16–72 caracteres e no máximo 72 bytes (BCrypt). Placeholders literais do .env.example, valores demonstrativos, sequências e repetições previsíveis são rejeitados. Comprimento e filtros não provam aleatoriedade: gere valores exclusivos com CSPRNG, por exemplo `openssl rand -base64 48` para JWT e `openssl rand -base64 24` para senha, em terminal privado. Injete as variáveis pelo gerenciador de secrets do ambiente; não cole saídas em PRs, logs, argumentos de processo ou arquivos versionados. O .env.example é apenas um modelo e seus placeholders são intencionalmente inválidos.

Combinar qualquer profile não produtivo (local/test/it) com outro ambiente (incluindo prod/production) interrompe o startup, mesmo com credenciais fortes. Produção deve ativar explicitamente prod. O GET legado também tem condição `local & !prod & !production`, como defesa adicional.

Trocar a variável de bootstrap não troca a senha de um administrador já persistido. Se credenciais demonstrativas já foram usadas, rotacione a conta existente por procedimento autorizado; rotacionar a chave JWT invalida tokens anteriores.

O profile `local` é uma opção consciente para desenvolvimento e demonstração:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
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

O handler omite mensagens brutas de exceção e registra somente o padrão da rota, nunca o path concreto/query. X-Correlation-Id aceita apenas UUID v4 canônico de 36 caracteres, sem sequências numéricas de documento; os demais valores são substituídos. IDs válidos continuam sendo dados não confiáveis, não uma prova de identidade.

Diagnósticos 5xx usam o logger separado oficina.restricted.diagnostics: tipo, traceId e até 12 posições da pilha da aplicação, sem mensagem, causas ou payloads. Logback desativa propagação para console e grava em DIAGNOSTICS_DIR (default logs/restricted), com retenção de 7 dias/100MB. A imagem cria diretório privado 0700 pertencente ao usuário da aplicação. Fora da imagem ou ao montar volumes, provisione diretório 0700/ACL equivalente e limite leitura à operação autorizada; não publique esse diretório nem o envie a coletores públicos. Proxy, ingress e APM precisam impedir captura de corpos e aplicar suas próprias políticas de PII.

Compose publica portas somente em 127.0.0.1. Ainda usa credenciais demonstrativas e interfaces Mailpit/Prometheus/Grafana locais; não é adequado para publicação na internet, inclusive por túnel/reverse proxy. Nenhum deploy foi feito.

## Critérios de aceite

- produção não inicia sem variáveis sensíveis ou com valores conhecidos/fracos;
- segredo e senha não aparecem em logs;
- admin autenticado acessa `/api/v1/admin/**`;
- anônimo recebe `401` e usuário não admin recebe `403`;
- consulta pública usa JSON e não coloca documento na query string;
- GET legado existe somente em `local` e está deprecated;
- migrations, domínio de OS e regras de estoque permanecem inalterados.
