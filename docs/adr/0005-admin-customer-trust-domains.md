# ADR 0005 — Separação temporária ADMIN HMAC / CUSTOMER RS256

Status: proposta para revisão técnica.

A Lambda emite sub UUID e role CUSTOMER; o legado administrativo usa username e HMAC. Compartilhar parser ou resolver mecanismo pelo alg permitiria misturar domínios de confiança.

Decisão: selecionar SecurityFilterChains pela rota. CUSTOMER usa OAuth2 Resource Server/Nimbus, JWKS configurado e validação estrita do contrato. ADMIN mantém login, chave HMAC e consulta persistida. Desabilitar registro servlet do filtro legado evita dupla execução e interferência entre cadeias. Não existe fallback. UUID tipado chega ao caso de uso que verifica propriedade persistida antes de projetar dados mínimos.

Consequências: dois mecanismos precisam ser operados temporariamente; falhas de JWKS negam acesso quando não há chave confiável dentro do TTL. Rotação exige sobreposição de chaves. Nenhuma chave privada da Lambda chega ao Java. Desenvolvimento local sem configuração CUSTOMER falha fechado para essas rotas.

Alternativas descartadas: usar HMAC para CUSTOMER; selecionar parser pelo header alg; resolver UUID em UserDetailsService; confiar em customerId enviado no request; substituir autenticação administrativa nesta entrega.

Evolução: migrar ADMIN para provedor de identidade unificado, com issuer/audience/roles explícitos, migração dos usuários, testes de isolamento e retirada coordenada do emissor/filtro HMAC. Ver [contrato e operação](../fase3/customer-rs256-integration.md).
