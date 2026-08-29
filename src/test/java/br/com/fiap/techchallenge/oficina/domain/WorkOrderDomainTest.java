package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderPartItem;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderServiceItem;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderDomainTest {

    @Test
    void shouldGenerateBudgetAutomaticallyAndWaitForApproval() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Troca de óleo", "Óleo", BigDecimal.valueOf(120), 40);
        var part = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 5, 1);

        order.addRequestedService(service, 1);
        order.addRequiredPart(part, 2);

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WAITING_APPROVAL);
        assertThat(order.getTotalServices()).isEqualByComparingTo("120.00");
        assertThat(order.getTotalParts()).isEqualByComparingTo("70.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("190.00");
        assertThat(part.getQuantityInStock()).isEqualTo(5);
    }

    @Test
    void shouldApproveBudgetAndReserveStockOnlyOnce() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Troca de óleo", "Óleo", BigDecimal.valueOf(120), 40);
        var part = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 5, 1);
        order.addRequestedService(service, 1);
        order.addRequiredPart(part, 2);

        order.approveBudget();

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.IN_EXECUTION);
        assertThat(part.getQuantityInStock()).isEqualTo(3);
        assertThat(order.getPartItems().getFirst().isStockReserved()).isTrue();
    }

    @Test
    void shouldRejectInitialBudgetWithoutReservingStockOrStartingExecution() {
        var order = sampleOrder();
        var part = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 5, 1);
        order.addRequiredPart(part, 2);

        order.rejectBudget();

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.BUDGET_REJECTED);
        assertThat(part.getQuantityInStock()).isEqualTo(5);
        assertThat(order.getPartItems().getFirst().isStockReserved()).isFalse();
        assertThat(order.getStartedAt()).isNull();
        assertThat(order.getFinishedAt()).isNull();
        assertThat(order.getDeliveredAt()).isNull();
    }

    @Test
    void shouldRejectBudgetRejectionWhenOrderIsNotWaitingForApproval() {
        var order = sampleOrder();

        assertThatThrownBy(order::rejectBudget)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("aguardando aprovação");

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.RECEIVED);
    }

    @Test
    void shouldRejectAdditionalBudgetRejectionAfterExecutionHasStarted() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Alinhamento", "Direção", BigDecimal.valueOf(150), 60);
        var additionalPart = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 5, 1);
        order.addRequestedService(service, 1);
        order.approveBudget();
        order.addRequiredPart(additionalPart, 2);

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WAITING_APPROVAL);
        assertThat(order.getStartedAt()).isNotNull();
        assertThatThrownBy(order::rejectBudget)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("orçamento adicional em OS já iniciada");

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WAITING_APPROVAL);
        assertThat(additionalPart.getQuantityInStock()).isEqualTo(5);
        assertThat(order.getPartItems().getFirst().isStockReserved()).isFalse();
    }

    @Test
    void shouldRequireApprovalAgainWhenAddingItemsDuringExecution() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Troca de óleo", "Óleo", BigDecimal.valueOf(120), 40);
        var additionalPart = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 5, 1);
        order.addRequestedService(service, 1);
        order.approveBudget();

        order.addRequiredPart(additionalPart, 2);

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WAITING_APPROVAL);
        assertThat(additionalPart.getQuantityInStock()).isEqualTo(5);
        assertThat(order.getPartItems().getFirst().isStockReserved()).isFalse();
        assertThatThrownBy(order::finish)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("em execução");

        order.approveBudget();

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.IN_EXECUTION);
        assertThat(additionalPart.getQuantityInStock()).isEqualTo(3);
        assertThat(order.getPartItems().getFirst().isStockReserved()).isTrue();
    }

    @Test
    void shouldRejectApprovalWhenPendingPartsExceedAvailableStockWithoutPartialReservation() {
        var order = sampleOrder();
        var part = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 3, 1);
        order.addRequiredPart(part, 2);
        order.addRequiredPart(part, 2);

        assertThatThrownBy(order::approveBudget)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Estoque insuficiente");

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WAITING_APPROVAL);
        assertThat(part.getQuantityInStock()).isEqualTo(3);
        assertThat(order.getPartItems()).allMatch(item -> !item.isStockReserved());
    }

    @Test
    void shouldFollowStatusLifecycle() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Alinhamento", "Direção", BigDecimal.valueOf(150), 60);

        order.startDiagnosis("Verificar suspensão");
        order.addRequestedService(service, 1);
        order.approveBudget();
        order.finish();
        order.deliver();

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.DELIVERED);
        assertThat(order.getFinishedAt()).isNotNull();
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    void shouldRejectInvalidTransition() {
        var order = sampleOrder();

        assertThatThrownBy(order::finish)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("em execução");
    }


    @Test
    void shouldUpdateDiagnosticNotesAndMoveReceivedOrderToDiagnosis() {
        var order = sampleOrder();

        order.updateDiagnosticNotes("Scanner apontou falha no sensor.");

        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.IN_DIAGNOSIS);
        assertThat(order.getDiagnosticNotes()).isEqualTo("Scanner apontou falha no sensor.");
    }

    @Test
    void shouldRejectApproveWithoutBudgetWaitingApproval() {
        var order = sampleOrder();

        assertThatThrownBy(order::approveBudget)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("aguardando aprovação");
    }

    @Test
    void shouldRejectAddingItemsAfterDelivery() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Alinhamento", "Direção", BigDecimal.valueOf(150), 60);
        order.addRequestedService(service, 1);
        order.approveBudget();
        order.finish();
        order.deliver();

        assertThatThrownBy(() -> order.addRequestedService(service, 1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("finalizada ou entregue");
    }

    @Test
    void shouldRehydrateReservedPartItemWhenCurrentStockIsZero() {
        var source = sampleOrder();
        var part = new Part("Filtro atual", "FILTER-001", BigDecimal.valueOf(40), 0, 1);
        var snapshot = new WorkOrder.PartItemSnapshot(
            UUID.randomUUID(), part, "Filtro contratado", "FILTER-001", new BigDecimal("35.00"),
            2, new BigDecimal("70.00"), true, null, null
        );

        var restored = WorkOrder.restore(
            UUID.randomUUID(), "OS-HIST-001", source.getCustomer(), source.getVehicle(),
            WorkOrderStatus.IN_EXECUTION, "Diagnóstico histórico", null, null, null, null,
            new BigDecimal("0.00"), new BigDecimal("70.00"), new BigDecimal("70.00"),
            List.of(), List.of(snapshot), null, null
        );

        var restoredItem = restored.getPartItems().getFirst();
        assertThat(restoredItem.getPartName()).isEqualTo("Filtro contratado");
        assertThat(restoredItem.getUnitPrice()).isEqualByComparingTo("35.00");
        assertThat(restoredItem.getLineTotal()).isEqualByComparingTo("70.00");
        assertThat(restoredItem.isStockReserved()).isTrue();
        assertThat(part.getQuantityInStock()).isZero();
    }

    @Test
    void shouldRehydrateHistoricalItemWithInactiveService() {
        var source = sampleOrder();
        var service = new ServiceCatalogItem(
            "Serviço atual", "Descrição atual", BigDecimal.valueOf(150), 60
        );
        service.deactivate();
        var snapshot = new WorkOrder.ServiceItemSnapshot(
            UUID.randomUUID(), service, "Serviço contratado", new BigDecimal("120.00"),
            2, 80, new BigDecimal("240.00"), null, null
        );

        var restored = WorkOrder.restore(
            UUID.randomUUID(), "OS-HIST-002", source.getCustomer(), source.getVehicle(),
            WorkOrderStatus.IN_EXECUTION, "Diagnóstico histórico", null, null, null, null,
            new BigDecimal("240.00"), new BigDecimal("0.00"), new BigDecimal("240.00"),
            List.of(snapshot), List.of(), null, null
        );

        var restoredItem = restored.getServiceItems().getFirst();
        assertThat(service.isActive()).isFalse();
        assertThat(restoredItem.getServiceName()).isEqualTo("Serviço contratado");
        assertThat(restoredItem.getUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(restoredItem.getEstimatedMinutes()).isEqualTo(80);
        assertThat(restoredItem.getLineTotal()).isEqualByComparingTo("240.00");
    }

    @Test
    void shouldStillRejectNewPartItemWhenStockIsInsufficient() {
        var order = sampleOrder();
        var part = new Part("Filtro", "FILTER-001", BigDecimal.valueOf(35), 1, 1);

        assertThatThrownBy(() -> new WorkOrderPartItem(order, part, 2))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void shouldStillRejectNewServiceItemWhenServiceIsInactive() {
        var order = sampleOrder();
        var service = new ServiceCatalogItem("Alinhamento", "Direção", BigDecimal.valueOf(150), 60);
        service.deactivate();

        assertThatThrownBy(() -> new WorkOrderServiceItem(order, service, 1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Serviço inativo");
    }

    private WorkOrder sampleOrder() {
        var customer = new Customer("Maria Silva", DocumentType.CPF, "52998224725", "maria@email.com", "31999999999");
        var vehicle = new Vehicle(customer, "ABC1D23", "Fiat", "Argo", 2020);
        return new WorkOrder("OS-TEST-001", customer, vehicle, "Cliente relata barulho.");
    }
}
