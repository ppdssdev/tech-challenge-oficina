package br.com.fiap.techchallenge.oficina.api.dto.catalog;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PartResponse(
    UUID id,
    String name,
    String sku,
    BigDecimal unitPrice,
    int quantityInStock,
    int minimumStock,
    boolean belowMinimumStock,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static PartResponse from(Part part) {
        return new PartResponse(
            part.getId(), part.getName(), part.getSku(), part.getUnitPrice(), part.getQuantityInStock(),
            part.getMinimumStock(), part.isBelowMinimumStock(), part.isActive(), part.getCreatedAt(), part.getUpdatedAt()
        );
    }
}
