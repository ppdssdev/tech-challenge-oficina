package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Channel;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.NotificationOutboxMessage;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Type;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOutboxPersistenceIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    NotificationOutboxPort notificationOutbox;

    @Test
    void shouldEnqueueBudgetDecisionAsPending() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(message("OS-001"));

        assertThat(queued.status()).isEqualTo(Status.PENDING);
        assertThat(queued.attempts()).isZero();
        assertThat(queued.createdAt()).isNotNull();
        assertThat(notificationOutbox.findPending(10))
            .singleElement()
            .satisfies(persisted -> {
                assertThat(persisted.id()).isEqualTo(queued.id());
                assertThat(persisted.status()).isEqualTo(Status.PENDING);
                assertThat(Duration.between(queued.createdAt(), persisted.createdAt()).abs())
                    .isLessThan(Duration.ofMillis(1));
            });
    }

    @Test
    void shouldFindPendingMessagesOrderedByCreationTime() {
        NotificationOutboxMessage first = notificationOutbox.enqueueBudgetDecision(message("OS-001"));
        NotificationOutboxMessage second = notificationOutbox.enqueueBudgetDecision(message("OS-002"));

        assertThat(notificationOutbox.findPending(10))
            .extracting(NotificationOutboxMessage::id)
            .containsExactly(first.id(), second.id());
    }

    @Test
    void shouldMarkMessageAsSentAndFillSentAt() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(message("OS-001"));

        notificationOutbox.markSent(queued.id());

        Map<String, Object> row = findState(queued.id());
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(row.get("last_error")).isNull();
        assertThat(notificationOutbox.findPending(10)).isEmpty();
    }

    @Test
    void shouldMarkMessageAsFailedIncrementAttemptsAndRecordError() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(message("OS-001"));

        notificationOutbox.markFailed(queued.id(), "SMTP indisponível");

        Map<String, Object> row = findState(queued.id());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("SMTP indisponível");
        assertThat(notificationOutbox.findPending(10)).isEmpty();
    }

    private Map<String, Object> findState(UUID id) {
        return jdbcTemplate.queryForMap("""
            select status, attempts, last_error, sent_at
              from notification_outbox
             where id = ?
            """, id);
    }

    private static NotificationOutboxMessage message(String workOrderCode) {
        return new NotificationOutboxMessage(
            UUID.randomUUID(), Type.BUDGET_DECISION, Channel.MAILPIT_EMAIL, Status.PENDING,
            "cliente@email.com", "Orçamento " + workOrderCode, "Corpo", workOrderCode,
            "http://localhost/approve", "http://localhost/reject", 0, null, null, null, null
        );
    }
}
