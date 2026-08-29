package br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.ServiceCatalogJpaEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;

public final class ServiceCatalogPersistenceMapper {
    private ServiceCatalogPersistenceMapper() { }

    public static ServiceCatalogItem toDomain(ServiceCatalogJpaEntity entity) {
        return ServiceCatalogItem.restore(
            entity.getId(), entity.getName(), entity.getDescription(), entity.getBasePrice(),
            entity.getEstimatedMinutes(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public static void updateEntity(ServiceCatalogItem domain, ServiceCatalogJpaEntity entity) {
        if (domain.getId() != null) entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setBasePrice(domain.getBasePrice());
        entity.setEstimatedMinutes(domain.getEstimatedMinutes());
        entity.setActive(domain.isActive());
    }
}
