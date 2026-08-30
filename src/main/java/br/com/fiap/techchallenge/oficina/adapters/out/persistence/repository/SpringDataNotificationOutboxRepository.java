package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.NotificationOutboxJpaEntity;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationOutboxRepository
    extends JpaRepository<NotificationOutboxJpaEntity, UUID> {

    List<NotificationOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(Status status, Pageable pageable);

    @Query(value = """
        select *
          from notification_outbox
         where status = 'PENDING'
            or (status = 'PROCESSING' and updated_at < :staleBefore)
         order by created_at
         limit :limit
         for update skip locked
        """, nativeQuery = true)
    List<NotificationOutboxJpaEntity> findClaimableForUpdate(
        @Param("limit") int limit,
        @Param("staleBefore") OffsetDateTime staleBefore
    );

    long countByStatus(Status status);
}
