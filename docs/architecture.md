# Arquitetura e DDD pragmático

## Estilo arquitetural

O projeto foi implementado como um monólito Spring Boot em camadas, adequado para um MVP. A separação principal é:

- `api`: controllers REST, DTOs e tratamento de erros.
- `application`: serviços de aplicação que orquestram os casos de uso.
- `domain`: entidades, regras de negócio, validações e ciclo de vida da OS.
- `infrastructure`: repositórios Spring Data JPA.
- `security`: autenticação JWT e filtro de autorização.
- `config`: configuração de segurança, Swagger/OpenAPI e bootstrap do admin.

## DDD aplicado

O DDD foi usado de forma pragmática. O domínio da oficina aparece diretamente no código:

- `Customer`: cliente identificado por CPF/CNPJ.
- `Vehicle`: veículo do cliente, identificado por placa.
- `DocumentNumber`: Value Object que normaliza e valida CPF/CNPJ.
- `VehiclePlate`: Value Object que normaliza e valida placas brasileiras antigas e Mercosul.
- `ServiceCatalogItem`: serviço oferecido pela oficina.
- `Part`: peça ou insumo com controle de estoque.
- `WorkOrder`: agregado central da ordem de serviço.
- `WorkOrderServiceItem` e `WorkOrderPartItem`: itens que compõem o orçamento.

## Agregado principal: WorkOrder

A `WorkOrder` concentra as principais regras:

- criação da OS em estado `RECEIVED`;
- inclusão de serviços e peças;
- geração automática dos totais do orçamento;
- mudança automática para `WAITING_APPROVAL` quando existe orçamento;
- aprovação do orçamento;
- baixa de estoque das peças apenas na aprovação;
- validação prévia do estoque total pendente antes de qualquer baixa;
- mudança para `IN_EXECUTION`, `FINALIZED` e `DELIVERED` conforme ações administrativas.

## Concorrência no estoque

O estoque é uma área crítica do domínio. Para reduzir risco de atualização concorrente:

- `Part` possui `@Version`, permitindo locking otimista e detecção de conflito de versão.
- Os endpoints de baixa/incremento de estoque carregam a peça com `PESSIMISTIC_WRITE`.
- A aprovação de orçamento carrega os itens de peças com lock pessimista antes de validar e reservar estoque.

Essa abordagem mantém o modelo simples para o MVP, mas já demonstra uma preocupação arquitetural real com consistência transacional.

## Ciclo de status

- `RECEIVED`: OS recebida.
- `IN_DIAGNOSIS`: OS em diagnóstico.
- `WAITING_APPROVAL`: orçamento gerado e aguardando autorização.
- `IN_EXECUTION`: orçamento aprovado e serviço em execução.
- `FINALIZED`: serviço finalizado.
- `DELIVERED`: veículo entregue ao cliente.

## Decisões de MVP

- O envio do orçamento ao cliente é representado pelo status `WAITING_APPROVAL` e pelo retorno da API com os valores calculados.
- A aprovação adicional de reparos é feita pelo endpoint administrativo `POST /api/v1/admin/work-orders/{id}/approve`.
- A consulta do cliente é pública, mas exige código da OS e CPF/CNPJ para reduzir exposição de dados.
- O banco escolhido foi PostgreSQL por robustez transacional, integridade referencial, bom suporte a UUID, tipos numéricos precisos e uso comum em sistemas administrativos.
