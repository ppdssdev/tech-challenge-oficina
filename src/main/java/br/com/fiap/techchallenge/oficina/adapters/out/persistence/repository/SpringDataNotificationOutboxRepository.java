package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.NotificationOutboxJpaEntity;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationOutboxRepository
    extends JpaRepository<NotificationOutboxJpaEntity, UUID> {

    List<NotificationOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(Status status, Pageable pageable);
}
