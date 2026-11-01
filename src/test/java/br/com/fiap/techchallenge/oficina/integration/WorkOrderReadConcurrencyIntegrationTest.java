package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.*;
import br.com.fiap.techchallenge.oficina.domain.model.customer.*;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

class WorkOrderReadConcurrencyIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired WorkOrderRepositoryPort orders;
    @Autowired CustomerRepositoryPort customers;
    @Autowired VehicleRepositoryPort vehicles;
    @Autowired TransactionPort tx;
    @Autowired GetWorkOrderUseCase getOrder;

    public static class SqlCapture implements StatementInspector {
        static final ThreadLocal<List<String>> SQL = new ThreadLocal<>();
        @Override public String inspect(String sql) {
            if (SQL.get() != null) SQL.get().add(sql.toLowerCase(Locale.ROOT));
            return sql;
        }
    }
    @DynamicPropertySource static void capture(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector", () -> SqlCapture.class.getName());
    }

    @Test void concurrentReadsCompleteWhileWriterHoldsExclusiveLock() throws Exception {
        var order = tx.required(() -> {
            var customer = customers.save(new Customer("Maria", DocumentType.CPF, "52998224725", null, null));
            var vehicle = vehicles.save(new Vehicle(customer, "ABC1D23", "Fiat", "Uno", 2020));
            return orders.save(new WorkOrder("OS-READ", customer, vehicle, null));
        });
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var writer = executor.submit(() -> tx.required(() -> {
                orders.findDetailedByIdForUpdate(order.getId()).orElseThrow();
                locked.countDown();
                try { assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var detailed = executor.submit(() -> captureSql(() -> tx.required(() -> {
                    assertThat(orders.findDetailedById(order.getId())).isPresent();
                    return null;
                })));
                var customer = executor.submit(() -> captureSql(() -> {
                    assertThat(getOrder.getForCustomer(order.getId(), order.getCustomer().getId()).id()).isEqualTo(order.getId());
                }));
                var detailedSql = detailed.get(5, TimeUnit.SECONDS);
                var customerSql = customer.get(5, TimeUnit.SECONDS);
                assertThat(detailedSql).hasSize(3);
                assertThat(customerSql).hasSize(1);
                assertThat(customerSql.getFirst()).doesNotContain("join", "work_order_part_items", "work_order_service_items");
                for (var sql : List.of(detailedSql, customerSql))
                    assertThat(sql).allSatisfy(statement -> assertThat(statement).doesNotContain(" for update", " for no key update", " for share"));
            } finally { release.countDown(); }
            writer.get(5, TimeUnit.SECONDS);
        }
    }

    private List<String> captureSql(Runnable work) {
        var sql = new ArrayList<String>();
        SqlCapture.SQL.set(sql);
        try { work.run(); return sql; }
        finally { SqlCapture.SQL.remove(); }
    }
}
