package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.adapters.out.observability.BusinessMetricsReader;
import br.com.fiap.techchallenge.oficina.application.port.out.*;
import br.com.fiap.techchallenge.oficina.domain.model.customer.*;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.*;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class BusinessMetricsIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired BusinessMetricsReader reader;
    @Autowired CustomerRepositoryPort customers;
    @Autowired VehicleRepositoryPort vehicles;
    @Autowired WorkOrderRepositoryPort orders;
    @Autowired TransactionPort tx;

    @Test
    void aggregatesCivilDayRealCompletedIntervalsRepeatedStatusesAndOpenAge() {
        Instant now = Instant.parse("2026-11-03T15:00:00Z");
        var customer = tx.required(() -> customers.save(new Customer("Fixture", DocumentType.CPF, "52998224725", null, null)));
        var vehicle = tx.required(() -> vehicles.save(new Vehicle(customer, "ABC1D23", "Fiat", "Uno", 2020)));
        var order = create(customer, vehicle);
        var legacy = create(customer, vehicle);
        var yesterday = create(customer, vehicle);
        var tomorrow = create(customer, vehicle);
        jdbcTemplate.update("delete from work_order_status_history");
        setCreated(order, "2026-11-03T03:00:00Z");
        setCreated(legacy, "2026-11-03T15:00:00Z");
        setCreated(yesterday, "2026-11-03T02:59:59Z");
        setCreated(tomorrow, "2026-11-04T03:00:00Z");
        WorkOrderStatus[] states = {WorkOrderStatus.RECEIVED, WorkOrderStatus.IN_DIAGNOSIS,
            WorkOrderStatus.WAITING_APPROVAL, WorkOrderStatus.IN_EXECUTION, WorkOrderStatus.WAITING_APPROVAL,
            WorkOrderStatus.IN_EXECUTION, WorkOrderStatus.FINALIZED};
        int[] offsets = {0, 10, 30, 60, 100, 150, 210};
        for (int i = 0; i < states.length; i++) {
            jdbcTemplate.update("""
                insert into work_order_status_history
                    (id, work_order_id, previous_status, new_status, occurred_at, event_order)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), order.getId(), i == 0 ? null : states[i - 1].name(), states[i].name(),
                now.minusSeconds(280).plusSeconds(offsets[i]).atOffset(ZoneOffset.UTC), i + 1);
        }
        jdbcTemplate.update("update work_orders set status = 'FINALIZED' where id = ?", order.getId());
        var snapshot = reader.read(now);
        assertThat(snapshot.created()).isEqualTo(2); // [03:00Z, next 03:00Z), not rolling 24h
        assertThat(snapshot.day()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(snapshot.completed().get(WorkOrderStatus.RECEIVED).seconds()).isEqualTo(10);
        assertThat(snapshot.completed().get(WorkOrderStatus.WAITING_APPROVAL).count()).isEqualTo(2);
        assertThat(snapshot.completed().get(WorkOrderStatus.WAITING_APPROVAL).seconds()).isEqualTo(80);
        assertThat(snapshot.completed().get(WorkOrderStatus.IN_EXECUTION).seconds()).isEqualTo(100);
        assertThat(snapshot.open()).containsOnlyKeys(WorkOrderStatus.FINALIZED);
        assertThat(snapshot.open().get(WorkOrderStatus.FINALIZED).count()).isEqualTo(1);
        assertThat(snapshot.open().get(WorkOrderStatus.FINALIZED).seconds()).isEqualTo(70);
        assertThat(reader.read(now)).isEqualTo(snapshot); // no accumulation on repeated collection
        assertThat(reader.read(now.plusSeconds(30)).open().get(WorkOrderStatus.FINALIZED).seconds()).isEqualTo(100);
        assertThat(reader.read(now.plusSeconds(30)).completed()).isEqualTo(snapshot.completed());
    }

    @Test
    void excludesRolledBackCreationAndDoesNotInventStartForLegacyFirstTransition() {
        var now = Instant.now();
        var customer = tx.required(() -> customers.save(new Customer("Fixture", DocumentType.CPF, "52998224725", null, null)));
        var vehicle = tx.required(() -> vehicles.save(new Vehicle(customer, "ABC1D23", "Fiat", "Uno", 2020)));
        assertThatThrownBy(() -> tx.required(() -> {
            orders.save(new WorkOrder("OS-ROLLBACK", customer, vehicle, null));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(reader.read(now).created()).isZero();
        var order = create(customer, vehicle);
        jdbcTemplate.update("delete from work_order_status_history where work_order_id = ?", order.getId());
        jdbcTemplate.update("""
            insert into work_order_status_history
                (id, work_order_id, previous_status, new_status, occurred_at, event_order)
            values (?, ?, 'RECEIVED', 'BUDGET_REJECTED', ?, 1)
            """, UUID.randomUUID(), order.getId(), now.minusSeconds(50).atOffset(ZoneOffset.UTC));
        jdbcTemplate.update("update work_orders set status = 'BUDGET_REJECTED' where id = ?", order.getId());
        var snapshot = reader.read(now);
        assertThat(snapshot.completed()).isEmpty();
        assertThat(snapshot.open()).isEmpty(); // terminal state does not accumulate open age
        jdbcTemplate.update("""
            insert into notification_outbox(id, type, channel, status, recipient, subject, body)
            values (?, 'BUDGET_DECISION', 'MAILPIT_EMAIL', 'FAILED', 'private@example.test', 'private', 'private')
            """, UUID.randomUUID());
        assertThat(reader.read(now).outbox()).containsEntry(NotificationOutboxPort.Status.FAILED, 1L);
    }

    private WorkOrder create(Customer customer, Vehicle vehicle) {
        return tx.required(() -> orders.save(new WorkOrder("OS-" + UUID.randomUUID().toString().substring(0, 8), customer, vehicle, null)));
    }
    private void setCreated(WorkOrder order, String date) {
        jdbcTemplate.update("update work_orders set created_at = ? where id = ?", OffsetDateTime.parse(date), order.getId());
    }
}
