# Validação JWT RS256 — etapa 7

Este documento descreve o validador PEM recuperado, usado nas operações por código
em `/api/v1/public/work-orders/**`. A cadeia `/api/v1/customer/**` mantém o validador
JWKS, cache limitado e principal `CustomerIdentity` da main; não há fallback entre
as cadeias. O flag `app.jwt.customer.enabled` controla somente o caminho PEM.
Veja também [a integração JWKS](../fase3/customer-rs256-integration.md) e
[o relatório da reconciliação](../fase3/reconciliation.md).

## Identidades e contrato

Conferido o emissor real em `tech-challenge-oficina-auth-lambda/auth/jwt.py`:
RS256, RSA >= 2048 bits, `kid`, `iss`, `aud`, `sub` UUID canônico, `role=CUSTOMER`,
`iat=nbf` e `exp=iat+900`, datas JWT em segundos.

O filtro usa apenas `alg` do header não verificado para escolher um validador fixo.
Isso não autentica nem fornece autoridades. Cada parser verifica a assinatura com
uma chave de tipo apropriado e impõe sua lista de algoritmos no localizador de chave.
Falhas não tentam o outro parser e resultam em 401 para uma credencial Bearer inválida.

- **HMAC administrativo:** HS256/HS384/HS512 mantidos, conforme o tamanho do segredo
  usado pelo emissor existente. O parser só fornece SecretKey a esses algoritmos.
  Exige expiração e `roles=[ROLE_ADMIN]`, rejeita `role`, `authorities`, `kid` e
  compressão. Depois consulta `UserDetailsService` e exige exclusivamente
  `ROLE_ADMIN` persistido. Claims não promovem o usuário. O principal continua
  sendo UserDetails administrativo. Login e emissão administrativos foram preservados.
- **RS256 cliente:** somente RS256 recebe chave RSA, exclusivamente do mapa local
  de kids autorizados. Exige issuer exato, audience configurada sem audiences extras,
  `role=CUSTOMER`, ausência de `roles`/`authorities` e UUID canônico em `sub`.
  `jku`, `jwk`, `x5u` e compressão são recusados; não são fontes de confiança.
  A identidade autenticada recebe apenas `ROLE_CUSTOMER`, com nome igual ao UUID.
  Nenhuma consulta a username administrativo é feita nesse caminho.

As três operações de cliente do Prompt 5 continuam comparando esse UUID ao
proprietário persistido da OS; ADMIN continua separado. Não há alteração na view,
na habilitação SQL do Prompt 6 ou no domínio.

## Datas e falha segura

`exp`, `nbf` e `iat` são obrigatórios. A tolerância de relógio é fixa em **30 segundos**.
JJWT verifica expiração e início de validade; a aplicação também rejeita iat futuro
além da tolerância, nbf diferente de iat, exp <= iat e duração acima de 900 segundos.
A tolerância não aumenta o TTL declarado. Relógios dos serviços devem estar sincronizados.

Tokens sem assinatura, adulterados, expirados, de algoritmo incompatível, kid
não autorizado ou claims incompatíveis recebem 401. Um CUSTOMER válido recebe 403
nas rotas ADMIN e nos recursos de outro cliente. UUID coincidente com username ADMIN
não modifica sua autoridade.

O validador PEM fica **desabilitado por padrão**; nesse estado rejeita todos os tokens RS256,
mas o login HMAC continua funcionando. Se habilitado, configuração ausente/incompleta,
arquivo ilegível, PEM inválido ou RSA menor que 2048 bits impede a inicialização.
Não há fallback para chave padrão, download, segredo HMAC ou leitura de claims sem
assinatura. Tokens são limitados a 8192 caracteres no filtro.

## Configuração externa para integração sintética

Fornecer um arquivo de configuração externo via mecanismo padrão do Spring Boot,
por exemplo `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/customer-jwt.yml`:

```yaml
app:
  jwt:
    customer:
      enabled: true
      issuer: https://synthetic-issuer.invalid
      audience: oficina-test
      public-keys:
        "[lambda-test-current]": /config/keys/current-public.pem
        "[lambda-test-next]": /config/keys/next-public.pem
```

Issuer, audience e os kids devem coincidir com a configuração do emissor de testes.
Os valores acima são ilustrativos, não são configuração implantada. Também há
`APP_JWT_CUSTOMER_ENABLED`, `APP_JWT_CUSTOMER_ISSUER` e `APP_JWT_CUSTOMER_AUDIENCE`;
o mapa de chaves pode ser fornecido pelo YAML externo ou `SPRING_APPLICATION_JSON`.

Os arquivos devem ser PEM X.509 SubjectPublicKeyInfo (`BEGIN PUBLIC KEY`), RSA de
pelo menos 2048 bits, legíveis pela API e protegidos contra escrita não autorizada.
Este validador PEM não lê chave privada, certificado, URL HTTP ou JWKS. Caminhos são configurados
pelo operador; nunca são derivados de `kid` recebido. Chaves são carregadas apenas
na inicialização e ficam em mapa imutável durante a execução.

O fluxo de emissão por CPF **não foi publicado nem habilitado**. A opção de validação
serve nesta etapa à integração com identidades sintéticas controladas. Não habilitar
emissão pública em produção antes de existir verificação adicional de identidade:
conhecer um CPF não comprova titularidade. Nenhuma rota de emissão foi criada na API.

## Rotação

1. Obter a nova chave pública por canal confiável e atribuir um novo kid único.
2. Configurar ambas as chaves públicas e reiniciar/atualizar todas as instâncias da API.
3. Só depois configurar o emissor para assinar com a nova chave/kid, em etapa autorizada.
4. Manter a antiga por pelo menos 930 segundos após a última emissão antiga
   (900 de TTL + 30 de tolerância), considerando a conclusão do rollout do emissor.
5. Remover o kid antigo e reiniciar/atualizar todas as instâncias. Tokens antigos
   passam a receber 401; não reutilizar kid para material de chave diferente.

Em comprometimento, remover a chave antiga imediatamente e aceitar a invalidação
dos tokens correspondentes. Não existe atualização automática nem revogação individual.

## Testes e limites

Os novos testes geram pares RSA efêmeros **somente em memória de teste** e escrevem
apenas os PEMs públicos temporários. Nenhuma chave privada ou senha nova é versionada.
A assinatura de fixtures usa JSON e a API criptográfica do Java independentemente
do encoder JJWT, reproduzindo os claims e header PyJWT do emissor real.

Cobertura: recursos próprios/alheios em consulta, aprovação e recusa, bloqueio ADMIN,
HMAC real, adulteração, assinatura incorreta/ausente, algoritmos trocados, confusão
com bytes da chave pública como segredo HMAC, role/roles/authorities, kid, iss, aud,
exp/nbf/iat, claims ausentes, TTL, UUID, rotação, tolerância e configuração inválida.
Também verifica colisão entre UUID cliente e username ADMIN e HMAC tentando promover
um usuário persistido CUSTOMER.

Não foi invocada a Lambda Python nesta etapa; seu contrato foi lido e reproduzido
nos testes. Testes fim a fim com o processo Python, implantação, credenciais e AWS
continuam pendentes. A habilitação `auth.customer_access` é verificada pela Lambda
na emissão, não a cada requisição da API; inativação não revoga um JWT já emitido.
A proteção contra impersonação por CPF requer fator adicional antes de uso público.

Nenhum repositório irmão, stack Terraform ou contrato SQL foi modificado. Nenhuma
operação AWS, branch, commit ou PR foi criada. Alterações locais anteriores preservadas.

## Evidência histórica do commit recuperado (antes da reconciliação)

- `mvn -B clean verify`: BUILD SUCCESS; 144 testes, zero falhas, erros ou ignorados.
- Regressões dos Prompts 5 e 6 incluídas; limites JaCoCo aprovados.
- Cobertura de linhas: JwtAuthenticationFilter 39/39; CustomerJwtVerifier 58/59;
  JwtTokenSecurityAdapter 31/32.
- `git diff --check`: aprovado.
- A primeira execução detectou incompatibilidade do `sig().clear()` no JJWT instalado;
  a implementação final restringe os algoritmos no localizador de chave e passou
  todos os testes de troca de algoritmo e confusão entre HMAC/RSA.
- Não executados: chamada ao processo Lambda Python ou integração AWS.
