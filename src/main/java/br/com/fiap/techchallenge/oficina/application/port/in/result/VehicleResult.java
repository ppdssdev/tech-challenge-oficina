package br.com.fiap.techchallenge.oficina.application.port.in.result;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleResult(
    UUID id,
    UUID customerId,
    String customerName,
    String customerDocumentNumber,
    String plate,
    String brand,
    String model,
    int manufacturingYear,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
