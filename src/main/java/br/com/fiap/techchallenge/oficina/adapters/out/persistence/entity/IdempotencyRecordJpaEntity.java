package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import br.com.fiap.techchallenge.oficina.application.port.out.IdempotencyRecord.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "IdempotencyRecordJpaEntity")
@Table(name = "idempotency_records")
public class IdempotencyRecordJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 80)
    private String operation;
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;
    @Column(name = "resource_code", length = 80)
    private String resourceCode;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public UUID getId() { return id; }
    public String getOperation() { return operation; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Status getStatus() { return status; }
    public String getResourceCode() { return resourceCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getLastError() { return lastError; }
}
