package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.IdempotencyRecordJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataIdempotencyRecordRepository
    extends JpaRepository<IdempotencyRecordJpaEntity, UUID> {

    @Modifying
    @Query(value = """
        insert into idempotency_records (
            id, operation, idempotency_key, request_hash, status, resource_code,
            created_at, updated_at
        ) values (
            :id, :operation, :key, :requestHash, 'PROCESSING', :resourceCode,
            now(), now()
        ) on conflict (operation, idempotency_key) do nothing
        """, nativeQuery = true)
    int insertProcessingIfAbsent(
        @Param("id") UUID id,
        @Param("operation") String operation,
        @Param("key") String key,
        @Param("requestHash") String requestHash,
        @Param("resourceCode") String resourceCode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRecordJpaEntity> findByOperationAndIdempotencyKey(
        String operation,
        String idempotencyKey
    );

    @Modifying
    @Query("""
        update IdempotencyRecordJpaEntity record
           set record.status = br.com.fiap.techchallenge.oficina.application.port.out.IdempotencyRecord.Status.COMPLETED,
               record.resourceCode = :resourceCode,
               record.updatedAt = CURRENT_TIMESTAMP,
               record.completedAt = CURRENT_TIMESTAMP,
               record.lastError = null
         where record.id = :id
        """)
    int markCompleted(@Param("id") UUID id, @Param("resourceCode") String resourceCode);
}
