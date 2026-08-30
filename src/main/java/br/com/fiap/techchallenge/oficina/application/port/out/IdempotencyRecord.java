package br.com.fiap.techchallenge.oficina.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdempotencyRecord(
    UUID id,
    String operation,
    String idempotencyKey,
    String requestHash,
    Status status,
    String resourceCode,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt,
    String lastError
) {
    public enum Status { PROCESSING, COMPLETED, FAILED }
}
