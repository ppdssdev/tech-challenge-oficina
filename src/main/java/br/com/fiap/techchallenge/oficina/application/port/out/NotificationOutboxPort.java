package br.com.fiap.techchallenge.oficina.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxPort {
    NotificationOutboxMessage enqueueBudgetDecision(NotificationOutboxMessage message);
    List<NotificationOutboxMessage> findPending(int limit);
    List<NotificationOutboxMessage> claimPending(int limit, OffsetDateTime staleBefore);
    long countByStatus(Status status);
    void markSent(UUID id);
    void markPending(UUID id, String errorMessage);
    void markFailed(UUID id, String errorMessage);

    enum Status { PENDING, PROCESSING, SENT, FAILED }
    enum Type { BUDGET_DECISION }
    enum Channel { MAILPIT_EMAIL }

    record NotificationOutboxMessage(
        UUID id,
        Type type,
        Channel channel,
        Status status,
        String recipient,
        String subject,
        String body,
        String workOrderCode,
        String approveUrl,
        String rejectUrl,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime sentAt
    ) {
    }
}
