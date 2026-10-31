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
    void shouldUpgradeV7WithoutInventingHistoryAndKeepLegacyCustomerActive() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = jdbcTemplate.getDataSource();
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema)
                .defaultSchema(schema).target("7").load().migrate();
            UUID customer = UUID.randomUUID();
            UUID vehicle = UUID.randomUUID();
            UUID order = UUID.randomUUID();
            jdbcTemplate.update("insert into " + schema + ".customers (id, full_name, document_type, document_number, created_at, updated_at) values (?, 'Legacy', 'CPF', '52998224725', now(), now())", customer);
            jdbcTemplate.update("insert into " + schema + ".vehicles (id, customer_id, plate, brand, model, manufacturing_year, created_at, updated_at) values (?, ?, 'ABC1D23', 'Fiat', 'Uno', 2020, now(), now())", vehicle, customer);
            jdbcTemplate.update("insert into " + schema + ".work_orders (id, code, customer_id, vehicle_id, status, total_services, total_parts, total_amount, created_at, updated_at) values (?, 'OS-LEGACY', ?, ?, 'IN_DIAGNOSIS', 0, 0, 0, now(), now())", order, customer, vehicle);
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema)
                .defaultSchema(schema).load().migrate();
            assertThat(jdbcTemplate.queryForObject("select active from " + schema + ".customers where id = ?", Boolean.class, customer)).isTrue();
            assertThat(jdbcTemplate.queryForObject("select count(*) from " + schema + ".work_order_status_history", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("select status from " + schema + ".work_orders where id = ?", String.class, order)).isEqualTo("IN_DIAGNOSIS");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

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
        String idempotencyTableName = jdbcTemplate.queryForObject(
            "select to_regclass('public.idempotency_records')::text",
            String.class
        );
        String statusConstraint = jdbcTemplate.queryForObject("""
            select pg_get_constraintdef(oid)
              from pg_constraint
             where conname = 'chk_notification_outbox_status'
            """, String.class);

        assertThat(successfulMigrations).isEqualTo(10);
        assertThat(tableName).isEqualTo("notification_outbox");
        assertThat(idempotencyTableName).isEqualTo("idempotency_records");
        assertThat(statusConstraint)
            .contains("PENDING")
            .contains("PROCESSING")
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
        for (String status : new String[] {"PENDING", "PROCESSING", "SENT", "FAILED"}) {
            insertOutboxRow(status);
        }

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notification_outbox",
            Integer.class
        )).isEqualTo(4);
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
