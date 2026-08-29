package br.com.fiap.techchallenge.oficina.application;

import br.com.fiap.techchallenge.oficina.application.port.in.ListWorkOrdersUseCase.StatusFilter;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.ListWorkOrdersService;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListWorkOrdersServiceTest {

    @Mock
    WorkOrderRepositoryPort workOrderRepository;

    private final TransactionPort transactions = TransactionPort.direct();

    @Test
    void shouldListOnlyOperationalWorkOrdersOrderedByPriorityAndOldestCreationDate() {
        var baseDate = OffsetDateTime.parse("2026-08-01T10:00:00-03:00");
        var received = workOrder("OS-RECEIVED", WorkOrderStatus.RECEIVED, baseDate.plusDays(1), null);
        var diagnosis = workOrder("OS-DIAGNOSIS", WorkOrderStatus.IN_DIAGNOSIS, baseDate.plusDays(2), null);
        var execution = workOrder("OS-EXECUTION", WorkOrderStatus.IN_EXECUTION, baseDate.plusDays(3), null);
        var newerApproval = workOrder("OS-APPROVAL-NEWER", WorkOrderStatus.WAITING_APPROVAL, baseDate.plusDays(4), null);
        var olderApproval = workOrder("OS-APPROVAL-OLDER", WorkOrderStatus.WAITING_APPROVAL, baseDate, null);
        var rejected = workOrder("OS-REJECTED", WorkOrderStatus.BUDGET_REJECTED, baseDate.minusDays(3), null);
        var finalized = workOrder("OS-FINALIZED", WorkOrderStatus.FINALIZED, baseDate.minusDays(1), null);
        var delivered = workOrder("OS-DELIVERED", WorkOrderStatus.DELIVERED, baseDate.minusDays(2), null);
        when(workOrderRepository.findAll()).thenReturn(List.of(
            received, finalized, diagnosis, newerApproval, rejected, delivered, execution, olderApproval
        ));
        var useCase = new ListWorkOrdersService(workOrderRepository, transactions);

        var result = useCase.list(null);

        assertThat(result).extracting(summary -> summary.status()).containsExactly(
            WorkOrderStatus.WAITING_APPROVAL,
            WorkOrderStatus.WAITING_APPROVAL,
            WorkOrderStatus.IN_EXECUTION,
            WorkOrderStatus.IN_DIAGNOSIS,
            WorkOrderStatus.RECEIVED
        );
        assertThat(result).extracting(summary -> summary.code()).containsExactly(
            "OS-APPROVAL-OLDER",
            "OS-APPROVAL-NEWER",
            "OS-EXECUTION",
            "OS-DIAGNOSIS",
            "OS-RECEIVED"
        );
    }

    @Test
    void shouldUseUpdatedAtWhenOperationalWorkOrderHasNoCreatedAt() {
        var baseDate = OffsetDateTime.parse("2026-08-01T10:00:00-03:00");
        var newer = workOrder("OS-NEWER", WorkOrderStatus.IN_EXECUTION, null, baseDate.plusDays(1));
        var older = workOrder("OS-OLDER", WorkOrderStatus.IN_EXECUTION, null, baseDate);
        when(workOrderRepository.findAll()).thenReturn(List.of(newer, older));
        var useCase = new ListWorkOrdersService(workOrderRepository, transactions);

        var result = useCase.list(null);

        assertThat(result).extracting(summary -> summary.code())
            .containsExactly("OS-OLDER", "OS-NEWER");
    }

    @Test
    void shouldRespectExplicitFinalizedStatusFilter() {
        var finalized = workOrder("OS-FINALIZED", WorkOrderStatus.FINALIZED, OffsetDateTime.now(), null);
        when(workOrderRepository.findByStatus(WorkOrderStatus.FINALIZED)).thenReturn(List.of(finalized));
        var useCase = new ListWorkOrdersService(workOrderRepository, transactions);

        var result = useCase.list(StatusFilter.FINALIZED);

        assertThat(result).extracting(summary -> summary.status())
            .containsExactly(WorkOrderStatus.FINALIZED);
        verify(workOrderRepository).findByStatus(WorkOrderStatus.FINALIZED);
        verify(workOrderRepository, never()).findAll();
    }

    @Test
    void shouldRespectExplicitDeliveredStatusFilter() {
        var delivered = workOrder("OS-DELIVERED", WorkOrderStatus.DELIVERED, OffsetDateTime.now(), null);
        when(workOrderRepository.findByStatus(WorkOrderStatus.DELIVERED)).thenReturn(List.of(delivered));
        var useCase = new ListWorkOrdersService(workOrderRepository, transactions);

        var result = useCase.list(StatusFilter.DELIVERED);

        assertThat(result).extracting(summary -> summary.status())
            .containsExactly(WorkOrderStatus.DELIVERED);
        verify(workOrderRepository).findByStatus(WorkOrderStatus.DELIVERED);
        verify(workOrderRepository, never()).findAll();
    }

    @Test
    void shouldRespectExplicitBudgetRejectedStatusFilter() {
        var rejected = workOrder("OS-REJECTED", WorkOrderStatus.BUDGET_REJECTED, OffsetDateTime.now(), null);
        when(workOrderRepository.findByStatus(WorkOrderStatus.BUDGET_REJECTED)).thenReturn(List.of(rejected));
        var useCase = new ListWorkOrdersService(workOrderRepository, transactions);

        var result = useCase.list(StatusFilter.BUDGET_REJECTED);

        assertThat(result).extracting(summary -> summary.status())
            .containsExactly(WorkOrderStatus.BUDGET_REJECTED);
        verify(workOrderRepository).findByStatus(WorkOrderStatus.BUDGET_REJECTED);
        verify(workOrderRepository, never()).findAll();
    }

    private WorkOrder workOrder(
        String code,
        WorkOrderStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        var customer = new Customer(
            "Maria Silva", DocumentType.CPF, "52998224725", "maria@email.com", "31999999999"
        );
        var vehicle = new Vehicle(customer, "ABC1D23", "Fiat", "Argo", 2020);
        return WorkOrder.restore(
            UUID.randomUUID(), code, customer, vehicle, status, null, null, null, null, null,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), createdAt, updatedAt
        );
    }
}
