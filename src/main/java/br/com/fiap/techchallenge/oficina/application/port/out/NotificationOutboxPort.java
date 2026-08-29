package br.com.fiap.techchallenge.oficina.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxPort {
    NotificationOutboxMessage enqueueBudgetDecision(NotificationOutboxMessage message);
    List<NotificationOutboxMessage> findPending(int limit);
    void markSent(UUID id);
    void markFailed(UUID id, String errorMessage);

    enum Status { PENDING, SENT, FAILED }
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
