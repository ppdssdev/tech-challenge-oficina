package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceCatalogResponse(
    UUID id,
    String name,
    String description,
    BigDecimal basePrice,
    int estimatedMinutes,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static ServiceCatalogResponse from(ServiceCatalogItem item) {
        return new ServiceCatalogResponse(
            item.getId(), item.getName(), item.getDescription(), item.getBasePrice(),
            item.getEstimatedMinutes(), item.isActive(), item.getCreatedAt(), item.getUpdatedAt()
        );
    }
}
