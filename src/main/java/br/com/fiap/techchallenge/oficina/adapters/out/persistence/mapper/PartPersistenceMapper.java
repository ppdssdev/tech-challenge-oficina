package br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.PartJpaEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;

public final class PartPersistenceMapper {
    private PartPersistenceMapper() { }

    public static Part toDomain(PartJpaEntity entity) {
        return Part.restore(
            entity.getId(), entity.getName(), entity.getSku(), entity.getUnitPrice(), entity.getQuantityInStock(),
            entity.getMinimumStock(), entity.isActive(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public static void updateEntity(Part domain, PartJpaEntity entity) {
        if (domain.getId() != null) entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setSku(domain.getSku());
        entity.setUnitPrice(domain.getUnitPrice());
        entity.setQuantityInStock(domain.getQuantityInStock());
        entity.setMinimumStock(domain.getMinimumStock());
        entity.setActive(domain.isActive());
    }
}
