package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.NotificationOutboxJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataNotificationOutboxRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationOutboxPersistenceAdapter implements NotificationOutboxPort {
    private final SpringDataNotificationOutboxRepository repository;

    public NotificationOutboxPersistenceAdapter(SpringDataNotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationOutboxMessage enqueueBudgetDecision(NotificationOutboxMessage message) {
        var entity = new NotificationOutboxJpaEntity();
        entity.setId(message.id());
        entity.setType(Type.BUDGET_DECISION);
        entity.setChannel(Channel.MAILPIT_EMAIL);
        entity.setStatus(Status.PENDING);
        entity.setRecipient(message.recipient());
        entity.setSubject(message.subject());
        entity.setBody(message.body());
        entity.setWorkOrderCode(message.workOrderCode());
        entity.setApproveUrl(message.approveUrl());
        entity.setRejectUrl(message.rejectUrl());
        entity.setAttempts(0);
        return toMessage(repository.save(entity));
    }

    @Override
    public List<NotificationOutboxMessage> findPending(int limit) {
        return repository.findByStatusOrderByCreatedAtAsc(Status.PENDING, PageRequest.of(0, limit))
            .stream().map(NotificationOutboxPersistenceAdapter::toMessage).toList();
    }

    @Override
    @Transactional
    public List<NotificationOutboxMessage> claimPending(int limit, OffsetDateTime staleBefore) {
        var claimed = repository.findClaimableForUpdate(limit, staleBefore);
        claimed.forEach(entity -> {
            entity.setStatus(Status.PROCESSING);
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setLastError(null);
        });
        repository.flush();
        return claimed.stream().map(NotificationOutboxPersistenceAdapter::toMessage).toList();
    }

    @Override
    public long countByStatus(Status status) {
        return repository.countByStatus(status);
    }

    @Override
    @Transactional
    public void markSent(UUID id) {
        var entity = required(id);
        entity.setStatus(Status.SENT);
        entity.setSentAt(OffsetDateTime.now());
        entity.setLastError(null);
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void markPending(UUID id, String errorMessage) {
        var entity = required(id);
        entity.setStatus(Status.PENDING);
        entity.setLastError(errorMessage);
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String errorMessage) {
        var entity = required(id);
        entity.setStatus(Status.FAILED);
        entity.setLastError(errorMessage);
        repository.saveAndFlush(entity);
    }

    private NotificationOutboxJpaEntity required(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Mensagem da outbox não encontrada: " + id));
    }

    private static NotificationOutboxMessage toMessage(NotificationOutboxJpaEntity entity) {
        return new NotificationOutboxMessage(
            entity.getId(), entity.getType(), entity.getChannel(), entity.getStatus(),
            entity.getRecipient(), entity.getSubject(), entity.getBody(), entity.getWorkOrderCode(),
            entity.getApproveUrl(), entity.getRejectUrl(), entity.getAttempts(), entity.getLastError(),
            entity.getCreatedAt(), entity.getUpdatedAt(), entity.getSentAt()
        );
    }
}
