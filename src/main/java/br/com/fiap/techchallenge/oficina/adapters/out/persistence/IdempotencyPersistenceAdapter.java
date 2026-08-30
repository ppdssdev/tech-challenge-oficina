package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.IdempotencyRecordJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataIdempotencyRecordRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.IdempotencyPort;
import br.com.fiap.techchallenge.oficina.application.port.out.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyPersistenceAdapter implements IdempotencyPort {
    private final SpringDataIdempotencyRecordRepository repository;

    public IdempotencyPersistenceAdapter(SpringDataIdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryCreateProcessing(
        String operation,
        String key,
        String requestHash,
        String resourceCode
    ) {
        return repository.insertProcessingIfAbsent(
            UUID.randomUUID(), operation, key, requestHash, resourceCode
        ) == 1;
    }

    @Override
    public Optional<IdempotencyRecord> findByOperationAndKeyForUpdate(String operation, String key) {
        return repository.findByOperationAndIdempotencyKey(operation, key)
            .map(IdempotencyPersistenceAdapter::toRecord);
    }

    @Override
    public void markCompleted(UUID id, String resourceCode) {
        if (repository.markCompleted(id, resourceCode) != 1) {
            throw new IllegalStateException("Registro de idempotência não encontrado: " + id);
        }
    }

    private static IdempotencyRecord toRecord(IdempotencyRecordJpaEntity entity) {
        return new IdempotencyRecord(
            entity.getId(), entity.getOperation(), entity.getIdempotencyKey(),
            entity.getRequestHash(), entity.getStatus(), entity.getResourceCode(),
            entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCompletedAt(),
            entity.getLastError()
        );
    }
}
