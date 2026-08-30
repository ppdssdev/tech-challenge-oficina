package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Channel;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.NotificationOutboxMessage;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Type;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import br.com.fiap.techchallenge.oficina.application.usecase.ProcessNotificationOutboxService;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    void shouldClaimPendingMessageAndNotReturnItToASecondClaim() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(message("OS-CLAIM"));

        List<NotificationOutboxMessage> firstClaim = notificationOutbox.claimPending(
            10, OffsetDateTime.now().minusMinutes(5)
        );
        List<NotificationOutboxMessage> secondClaim = notificationOutbox.claimPending(
            10, OffsetDateTime.now().minusMinutes(5)
        );

        assertThat(firstClaim)
            .singleElement()
            .satisfies(claimed -> {
                assertThat(claimed.id()).isEqualTo(queued.id());
                assertThat(claimed.status()).isEqualTo(Status.PROCESSING);
                assertThat(claimed.attempts()).isEqualTo(1);
            });
        assertThat(secondClaim).isEmpty();
        assertThat(notificationOutbox.countByStatus(Status.PROCESSING)).isEqualTo(1);
    }

    @Test
    void shouldRecoverStaleProcessingMessage() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(message("OS-STALE"));
        notificationOutbox.claimPending(10, OffsetDateTime.now().minusMinutes(5));
        jdbcTemplate.update(
            "update notification_outbox set updated_at = now() - interval '10 minutes' where id = ?",
            queued.id()
        );

        List<NotificationOutboxMessage> recovered = notificationOutbox.claimPending(
            10, OffsetDateTime.now().minusMinutes(5)
        );

        assertThat(recovered)
            .singleElement()
            .satisfies(message -> {
                assertThat(message.id()).isEqualTo(queued.id());
                assertThat(message.status()).isEqualTo(Status.PROCESSING);
                assertThat(message.attempts()).isEqualTo(2);
            });
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
        notificationOutbox.claimPending(10, OffsetDateTime.now().minusMinutes(5));

        notificationOutbox.markFailed(queued.id(), "SMTP indisponível");

        Map<String, Object> row = findState(queued.id());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("SMTP indisponível");
        assertThat(notificationOutbox.findPending(10)).isEmpty();
    }

    @Test
    void shouldReturnFailedMessageToPendingForAutomaticRetry() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(message("OS-RETRY"));
        notificationOutbox.claimPending(10, OffsetDateTime.now().minusMinutes(5));

        notificationOutbox.markPending(queued.id(), "SMTP indisponível");

        Map<String, Object> row = findState(queued.id());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("SMTP indisponível");
    }

    @Test
    void shouldSendSingleMessageOnceWithTwoConcurrentProcessors() throws Exception {
        notificationOutbox.enqueueBudgetDecision(message("OS-CONCURRENT"));
        AtomicInteger sends = new AtomicInteger();
        NotificationPort notification = ignored -> sends.incrementAndGet();
        var firstProcessor = new ProcessNotificationOutboxService(
            notificationOutbox, notification, 10, 3, 300
        );
        var secondProcessor = new ProcessNotificationOutboxService(
            notificationOutbox, notification, 10, 3, 300
        );
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(
                () -> firstProcessor.processPending().processed(),
                () -> secondProcessor.processPending().processed()
            );

            var results = executor.invokeAll(tasks);

            assertThat(results)
                .extracting(future -> future.get(10, TimeUnit.SECONDS))
                .containsExactlyInAnyOrder(0, 1);
            assertThat(sends).hasValue(1);
            assertThat(notificationOutbox.countByStatus(Status.SENT)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCountMessagesByStatusForObservability() {
        NotificationOutboxMessage pending = notificationOutbox.enqueueBudgetDecision(message("OS-001"));
        NotificationOutboxMessage sent = notificationOutbox.enqueueBudgetDecision(message("OS-002"));
        NotificationOutboxMessage failed = notificationOutbox.enqueueBudgetDecision(message("OS-003"));

        notificationOutbox.markSent(sent.id());
        notificationOutbox.markFailed(failed.id(), "SMTP indisponível");

        assertThat(notificationOutbox.countByStatus(Status.PENDING)).isEqualTo(1);
        assertThat(notificationOutbox.countByStatus(Status.SENT)).isEqualTo(1);
        assertThat(notificationOutbox.countByStatus(Status.FAILED)).isEqualTo(1);
        assertThat(notificationOutbox.findPending(10))
            .extracting(NotificationOutboxMessage::id)
            .containsExactly(pending.id());
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
