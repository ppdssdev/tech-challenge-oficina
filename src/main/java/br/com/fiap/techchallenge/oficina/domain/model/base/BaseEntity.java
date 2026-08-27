package br.com.fiap.techchallenge.oficina.domain.model.base;

import java.time.OffsetDateTime;
import java.util.UUID;

public abstract class BaseEntity {

    private UUID id;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * Reconstitui metadados que pertencem ao ciclo de vida da entidade. A porta de
     * persistência usa este método ao materializar o domínio, sem expor JPA nele.
     */
    public final void restoreMetadata(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
