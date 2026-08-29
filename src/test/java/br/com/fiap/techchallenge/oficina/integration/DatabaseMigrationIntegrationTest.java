package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Channel;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.NotificationOutboxMessage;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Type;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseMigrationIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    NotificationOutboxPort notificationOutbox;

    @Test
    void shouldApplyFlywayMigrationsAndCreatePostgresOutboxConstraint() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success",
            Integer.class
        );
        String tableName = jdbcTemplate.queryForObject(
            "select to_regclass('public.notification_outbox')::text",
            String.class
        );
        String statusConstraint = jdbcTemplate.queryForObject("""
            select pg_get_constraintdef(oid)
              from pg_constraint
             where conname = 'chk_notification_outbox_status'
            """, String.class);

        assertThat(successfulMigrations).isEqualTo(5);
        assertThat(tableName).isEqualTo("notification_outbox");
        assertThat(statusConstraint)
            .contains("PENDING")
            .contains("SENT")
            .contains("FAILED");
    }

    @Test
    void shouldPersistAndFindPendingMessageThroughOutboxPort() {
        NotificationOutboxMessage queued = notificationOutbox.enqueueBudgetDecision(pendingMessage("OS-MIGRATION"));

        assertThat(queued.id()).isNotNull();
        assertThat(queued.status()).isEqualTo(Status.PENDING);
        assertThat(notificationOutbox.findPending(10))
            .extracting(NotificationOutboxMessage::id)
            .containsExactly(queued.id());
    }

    @Test
    void shouldEnforcePostgresOutboxStatusConstraint() {
        for (String status : new String[] {"PENDING", "SENT", "FAILED"}) {
            insertOutboxRow(status);
        }

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notification_outbox",
            Integer.class
        )).isEqualTo(3);
        assertThatThrownBy(() -> insertOutboxRow("UNKNOWN"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertOutboxRow(String status) {
        jdbcTemplate.update("""
            insert into notification_outbox (
                id, type, channel, status, recipient, subject, body, attempts, created_at, updated_at
            ) values (?, 'BUDGET_DECISION', 'MAILPIT_EMAIL', ?, 'cliente@email.com', 'Orçamento', 'Corpo', 0, now(), now())
            """, UUID.randomUUID(), status);
    }

    private static NotificationOutboxMessage pendingMessage(String workOrderCode) {
        return new NotificationOutboxMessage(
            UUID.randomUUID(), Type.BUDGET_DECISION, Channel.MAILPIT_EMAIL, Status.PENDING,
            "cliente@email.com", "Orçamento", "Corpo", workOrderCode,
            "http://localhost/approve", "http://localhost/reject", 0, null, null, null, null
        );
    }
}
