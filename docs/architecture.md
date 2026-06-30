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
- mudança para `IN_EXECUTION`, `FINALIZED` e `DELIVERED` conforme ações administrativas.

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
