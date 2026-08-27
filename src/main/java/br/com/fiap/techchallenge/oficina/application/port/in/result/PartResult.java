package br.com.fiap.techchallenge.oficina.application.port.in.result;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PartResult(
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
}
