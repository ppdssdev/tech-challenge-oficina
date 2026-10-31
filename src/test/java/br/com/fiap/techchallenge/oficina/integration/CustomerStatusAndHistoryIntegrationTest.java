package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.*;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.*;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.*;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class CustomerStatusAndHistoryIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired CustomerRepositoryPort customers;
    @Autowired ManageCustomersUseCase manageCustomers;
    @Autowired VehicleRepositoryPort vehicles;
    @Autowired WorkOrderRepositoryPort orders;
    @Autowired WorkOrderStatusHistoryPort history;
    @Autowired TransactionPort tx;

    @Test
    void shouldKeepCustomerAndLinksWhenDeletedAndAllowReactivation() {
        var order = createOrder();
        var id = order.getCustomer().getId();
        assertThat(manageCustomers.get(id).active()).isTrue();
        manageCustomers.delete(id);
        manageCustomers.delete(id);
        assertThat(manageCustomers.get(id).active()).isFalse();
        assertThat(customers.findByDocumentNumberAndActive("529.982.247-25", true)).isEmpty();
        assertThat(customers.findByDocumentNumberAndActive("529.982.247-25", false)).isPresent();
        assertThat(jdbcTemplate.queryForObject("select count(*) from vehicles where customer_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(manageCustomers.setActive(id, true).active()).isTrue();
        assertThat(customers.findByDocumentNumberAndActive("52998224725", true)).isPresent();
        assertThat(manageCustomers.setActive(id, false).active()).isFalse();
    }

    @Test
    void shouldPersistCreationDiagnosisAndIgnoreRepeatedSaveAndInvalidTransition() {
        var order = createOrder();
        var creation = history.findByWorkOrderId(order.getId());
        assertThat(creation).hasSize(1);
        assertThat(creation.getFirst().previousStatus()).isNull();
        assertThat(creation.getFirst().newStatus()).isEqualTo(WorkOrderStatus.RECEIVED);
        assertThat(creation.getFirst().occurredAt()).isNotNull();
        tx.required(() -> {
            var loaded = orders.findDetailedById(order.getId()).orElseThrow();
            assertThat(loaded.getStatusTransitions()).isEmpty();
            loaded.startDiagnosis("Diagnosis");
            orders.save(loaded);
            orders.save(loaded);
            assertThatThrownBy(loaded::finish).isInstanceOf(BusinessException.class);
            return null;
        });
        var events = history.findByWorkOrderId(order.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(1).previousStatus()).isEqualTo(WorkOrderStatus.RECEIVED);
        assertThat(events.get(1).newStatus()).isEqualTo(WorkOrderStatus.IN_DIAGNOSIS);
        assertThat(events.get(1).occurredAt()).isAfterOrEqualTo(events.getFirst().occurredAt());
    }

    @Test
    void shouldRollBackStatusAndHistoryTogether() {
        var order = createOrder();
        assertThatThrownBy(() -> tx.required(() -> {
            var loaded = orders.findDetailedById(order.getId()).orElseThrow();
            loaded.startDiagnosis("Diagnosis");
            orders.save(loaded);
            throw new IllegalStateException("Force rollback after flush");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(history.findByWorkOrderId(order.getId())).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select status from work_orders where id = ?", String.class, order.getId())).isEqualTo("RECEIVED");
    }

    @Test
    void shouldRollBackOrderWhenHistoryCannotBeWritten() {
        var order = createOrder();
        jdbcTemplate.execute("alter table work_order_status_history add constraint test_reject_diagnosis check (new_status <> 'IN_DIAGNOSIS')");
        try {
            assertThatThrownBy(() -> tx.required(() -> {
                var loaded = orders.findDetailedById(order.getId()).orElseThrow();
                loaded.startDiagnosis("Diagnosis");
                return orders.save(loaded);
            })).isInstanceOf(RuntimeException.class);
            assertThat(jdbcTemplate.queryForObject("select status from work_orders where id = ?", String.class, order.getId())).isEqualTo("RECEIVED");
            assertThat(history.findByWorkOrderId(order.getId())).hasSize(1);
        } finally {
            jdbcTemplate.execute("alter table work_order_status_history drop constraint test_reject_diagnosis");
        }
    }

    @Test
    void shouldSerializeConcurrentTransitionsFromSameState() throws Exception {
        var order = createOrder();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> transition = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    return tx.required(() -> {
                        var loaded = orders.findDetailedById(order.getId()).orElseThrow();
                        loaded.startDiagnosis("Diagnosis");
                        orders.save(loaded);
                        return true;
                    });
                } catch (BusinessException expected) { return false; }
            };
            var first = executor.submit(transition);
            var second = executor.submit(transition);
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        assertThat(history.findByWorkOrderId(order.getId())).hasSize(2);
    }

    private WorkOrder createOrder() {
        return tx.required(() -> {
            var customer = customers.save(new Customer("Maria", DocumentType.CPF, "52998224725", null, null));
            var vehicle = vehicles.save(new Vehicle(customer, "ABC1D23", "Fiat", "Uno", 2020));
            return orders.save(new WorkOrder("OS-" + UUID.randomUUID().toString().substring(0, 8), customer, vehicle, null));
        });
    }
}
