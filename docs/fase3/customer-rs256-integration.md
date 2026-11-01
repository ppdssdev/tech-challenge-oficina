# Integração CUSTOMER RS256 — Prompt 05

Referências inspecionadas: aplicação `61dead2ac89ecea2be2650e310b202a710d43fd9`; emissor Python `bbbf36eecd57a4f35e1792702a13264a7d74ea75` (repositório `ppdssdev/tech-challenge-oficina-auth-lambda`). Nenhuma alteração no emissor foi necessária.

## Domínios de confiança

A primeira SecurityFilterChain seleciona `/api/v1/customer/**` pela rota. OAuth2 Resource Server/Nimbus aceita apenas RS256 com chave RSA do JWKS configurado. Após assinatura, valida issuer, audience, kid não vazio no formato do emissor, UUID canônico, role exatamente CUSTOMER e presença/coerência de iat, nbf e exp. A duração máxima é 900 segundos; datas futuras respeitam skew de até 60 segundos. A autoridade concedida é exclusivamente ROLE_CUSTOMER; o principal é CustomerIdentity(UUID). Não há UserDetailsService nem fallback HMAC nessa cadeia.

A segunda cadeia preserva login e validação HMAC com usuário persistido, e exige ROLE_ADMIN em `/api/v1/admin/**`. O filtro legado tem registro servlet desabilitado para não interceptar requisições da primeira cadeia. Tokens RS256 não passam na validação HMAC. Credencial incompatível com a rota resulta em 401; usuário autenticado sem autoridade exigida recebe 403. As regras públicas e proteções da PR #1 permanecem.

## Autorização de objeto e contrato HTTP

`GET /api/v1/customer/work-orders/{id}` recebe identidade exclusivamente do principal validado. GetWorkOrderService lê WorkOrder via WorkOrderRepositoryPort dentro de TransactionPort, compara o UUID do relacionamento persistido Customer com a identidade e só então projeta `id`, `code`, `status`, `totalAmount`. Ausência e proprietário diferente geram a mesma exceção/404. Não há customerId de request, nome, documento, placa ou notas administrativas na resposta.

```mermaid
sequenceDiagram
    participant C as Cliente
    participant L as Lambda Python RS256Signer
    participant S as Spring CUSTOMER chain
    participant J as JWKS configurado
    participant U as GetWorkOrderService
    participant P as PostgreSQL
    C->>L: Solicitação de autenticação por CPF
    L-->>C: JWT RS256, sub UUID, role CUSTOMER, kid
    C->>S: GET customer/work-orders/id + Bearer
    S->>J: Chave pública RSA (cache limitado)
    S->>S: Verificar assinatura e claims
    S->>U: id da OS + UUID autenticado
    U->>P: Consultar OS e proprietário por port
    P-->>U: WorkOrder e Customer persistidos
    alt proprietário corresponde
        U-->>C: 200 com projeção mínima
    else ausente ou outro proprietário
        U-->>C: 404
    end
    Note over S: ADMIN usa outra cadeia e chave HMAC
```

## Configuração por ambiente

| Variável | Regra |
| --- | --- |
| CUSTOMER_JWKS_URI | URI explícita e confiável, exclusivamente da configuração |
| CUSTOMER_JWT_ISSUER | Valor exato do issuer configurado na Lambda |
| CUSTOMER_JWT_AUDIENCE | Audience da Lambda, `oficina-api-<environment>` |
| CUSTOMER_JWT_CLOCK_SKEW_SECONDS | 0–60, padrão 60 |
| CUSTOMER_JWKS_CACHE_SECONDS | 1–300, padrão 300 |

Sem perfil local/test/it, configuração ausente ou URI sem HTTPS impede startup. Perfis production/staging/cloud prevalecem sobre perfis locais. HTTP é permitido apenas para loopback nos perfis locais. Sem as três configurações, local/test/it preservam o desenvolvimento administrativo e rejeitam toda autenticação CUSTOMER. Configuração parcial é inválida. Não há endpoints AWS fictícios.

A origem confiável é o host explicitamente aprovado pelo operador em CUSTOMER_JWKS_URI, protegido junto à configuração da aplicação. Redirects HTTP são desabilitados, TLS é validado pelo cliente Java, conexão e leitura têm timeout de 2 segundos. Headers jku/x5u do token não definem destinos de rede. Somente o JWKS configurado participa da seleção de chave.

## Cache e rotação

Cache Caffeine tem uma entrada por decoder com expiração após escrita, sem renovação por leitura, máximo 300 segundos. Nimbus atualiza o conjunto ao encontrar kid desconhecido. Uma chave confiável ainda em cache permite validar durante indisponibilidade, até o TTL. Cache expirado, vazio, chave desconhecida ou erro de obtenção sem chave válida resultam em 401; não há stale cache ilimitado. JWT expirado continua inválido mesmo com chave em cache.

Publique nova chave com kid distinto junto à anterior antes de emitir tokens novos. Preserve a anterior por pelo menos 900 + 60 segundos após a última emissão com ela; considere também propagação do JWKS. Remover uma chave pode levar até o TTL para surtir efeito nos validadores. Revogação emergencial requer invalidar caches/reiniciar validadores e interromper emissão. Timeouts limitam espera, mas rate limiting na borda para requisições com kids desconhecidos permanece responsabilidade da futura infraestrutura.

## Testes reproduzíveis

Requer Java 21, Maven, Docker acessível e Python com venv:

```bash
python3 -m venv /tmp/customer-issuer-venv
/tmp/customer-issuer-venv/bin/pip install -r src/test/python/requirements.txt
CUSTOMER_TEST_PYTHON=/tmp/customer-issuer-venv/bin/python mvn -B clean verify
```

`src/test/python/auth` contém cópia exata dos módulos necessários do commit Python, com proveniência. `tokens.py` gera RSA descartável em memória e chama RS256Signer.sign para o token positivo. Os negativos derivam do contrato e são assinados em Python. Não há chave privada persistida, token real ou dependência AWS. CustomerJwtIntegrationTest inicia servidor JWKS controlado e usa HTTP real contra Spring em porta aleatória, persistindo Customer/Vehicle/WorkOrder em PostgreSQL 16 via Testcontainers. Testa acesso próprio, 404 de terceiros/ausente, login admin, isolamento bidirecional, adulteração, claims ausentes/incorretas, algoritmos indevidos, cache, rotação, indisponibilidade e endpoints públicos. A CI usa Python 3.13, compatível com o runtime da Lambda, e instala as dependências Python antes de executar Maven; JaCoCo mantém o gate de 80% nos pacotes de domínio.

## Riscos e próxima etapa

CPF sozinho não comprova identidade: o mecanismo do emissor precisa evoluir para verificação adicional. Inativação do cliente não revoga automaticamente token emitido: janela nominal de 15 minutos, acrescida de até 60 segundos de tolerância. Chaves públicas são distribuídas no JWKS; chaves privadas permanecem apenas no emissor. Publicação HTTPS real do JWKS, DNS/TLS, distribuição/cache, armazenamento privado e rotação das chaves, configuração dos ambientes e controles de tráfego dependem da infraestrutura AWS futura. Nenhuma infraestrutura ou deploy foi executado aqui. ADMIN HMAC é dívida temporária até migração para provedor de identidade unificado.

## Evidência de execução local

Execução local com Python 3.12.3 (a CI fixa Python 3.13) de `mvn -B clean verify` com CUSTOMER_TEST_PYTHON apontando para venv: 145 testes, zero falhas, zero erros, zero ignorados; BUILD SUCCESS. PostgreSQL 16.15 em Testcontainers; migrations V1–V10 aplicadas e testes de migração aprovados. Gate JaCoCo aprovado: pacotes de domínio entre 84,6% e 100% de linhas.

CustomerJwtIntegrationTest: 5 cenários HTTP aprovados, incluindo matriz de tokens negativos. Token positivo produzido por RS256Signer → endpoint próprio 200; mesmo endpoint com identidade de outro cliente 404; UUID inexistente 404; CUSTOMER → ADMIN 401; login ADMIN → endpoint administrativo 200; ADMIN → CUSTOMER 401. Outage de JWKS com cache expirado retorna 401 Bearer diretamente, sem dispatch de erro à cadeia administrativa.

SHA-256 dos arquivos do emissor copiados, iguais aos originais do commit referenciado:

- auth/jwt.py: `c6f2ef211294baff5b81bc03cb9a96e924d3c199a775fbbe11cad0dc8da15a97`
- auth/ports.py: `6ae670220b5b26c67ef9b13887fcea2ff910c89f078ef896819b4b3a90c0aed2`

O resultado remoto de GitHub Actions deve ser consultado nos checks da PR; a execução local não substitui esse resultado.
