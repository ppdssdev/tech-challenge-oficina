package br.com.fiap.techchallenge.oficina.application.port.in.result;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceCatalogResult(
    UUID id,
    String name,
    String description,
    BigDecimal basePrice,
    int estimatedMinutes,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
