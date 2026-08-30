package br.com.fiap.techchallenge.oficina.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyPort {
    boolean tryCreateProcessing(String operation, String key, String requestHash, String resourceCode);
    Optional<IdempotencyRecord> findByOperationAndKeyForUpdate(String operation, String key);
    void markCompleted(UUID id, String resourceCode);
}
