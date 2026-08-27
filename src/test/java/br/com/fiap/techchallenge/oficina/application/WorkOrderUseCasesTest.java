package br.com.fiap.techchallenge.oficina.application;

import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase.DefaultCommand;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.AddWorkOrderItemsService;
import br.com.fiap.techchallenge.oficina.application.usecase.CalculateWorkOrderMetricsService;
import br.com.fiap.techchallenge.oficina.application.usecase.DecideBudgetService;
import br.com.fiap.techchallenge.oficina.application.usecase.GetWorkOrderService;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderUseCasesTest {

    @Mock
    WorkOrderRepositoryPort workOrderRepository;

    @Mock
    ServiceCatalogRepositoryPort serviceCatalogRepository;

    @Mock
    PartRepositoryPort partRepository;

    private final TransactionPort transactions = TransactionPort.direct();

    @Test
    void shouldRejectApproveBudgetWhenWorkOrderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(workOrderRepository.findDetailedByIdForStockUpdate(id)).thenReturn(Optional.empty());

        var useCase = new DecideBudgetService(workOrderRepository, partRepository, transactions);

        assertThatThrownBy(() -> useCase.approve(id))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Ordem de serviço não encontrada");
    }

    @Test
    void shouldRejectAddItemsWhenRequestHasNoItems() {
        UUID id = UUID.randomUUID();
        var order = sampleOrder();
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.of(order));
        var useCase = new AddWorkOrderItemsService(
            workOrderRepository, serviceCatalogRepository, partRepository, transactions
        );

        assertThatThrownBy(() -> useCase.add(id, new DefaultCommand(List.of(), List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Informe ao menos um serviço ou peça");
    }

    @Test
    void shouldHidePublicStatusWhenDocumentDoesNotMatch() {
        var order = sampleOrder();
        when(workOrderRepository.findByCode(order.getCode())).thenReturn(Optional.of(order));
        var useCase = new GetWorkOrderService(workOrderRepository, transactions);

        assertThatThrownBy(() -> useCase.getPublicStatus(order.getCode(), "00000000000"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Ordem de serviço não encontrada");
    }

    @Test
    void shouldQueryAverageExecutionTimeWithoutDateFilters() {
        when(workOrderRepository.findCompletedForMetrics()).thenReturn(List.of());
        var useCase = new CalculateWorkOrderMetricsService(workOrderRepository, transactions);

        var response = useCase.averageExecutionTime(null, null);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetrics();
    }

    @Test
    void shouldQueryAverageExecutionTimeWithFromFilterOnly() {
        var from = OffsetDateTime.parse("2026-06-30T00:00:00-03:00");
        when(workOrderRepository.findCompletedForMetricsFrom(from))
            .thenReturn(List.of());
        var useCase = new CalculateWorkOrderMetricsService(workOrderRepository, transactions);

        var response = useCase.averageExecutionTime(from, null);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetricsFrom(from);
    }

    @Test
    void shouldQueryAverageExecutionTimeWithToFilterOnly() {
        var to = OffsetDateTime.parse("2026-06-30T23:59:59-03:00");
        when(workOrderRepository.findCompletedForMetricsTo(to))
            .thenReturn(List.of());
        var useCase = new CalculateWorkOrderMetricsService(workOrderRepository, transactions);

        var response = useCase.averageExecutionTime(null, to);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetricsTo(to);
    }

    @Test
    void shouldQueryAverageExecutionTimeWithDateRange() {
        var from = OffsetDateTime.parse("2026-06-30T00:00:00-03:00");
        var to = OffsetDateTime.parse("2026-06-30T23:59:59-03:00");
        when(workOrderRepository.findCompletedForMetricsBetween(from, to))
            .thenReturn(List.of());
        var useCase = new CalculateWorkOrderMetricsService(workOrderRepository, transactions);

        var response = useCase.averageExecutionTime(from, to);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetricsBetween(from, to);
    }

    private WorkOrder sampleOrder() {
        var customer = new Customer("Maria Silva", DocumentType.CPF, "52998224725", "maria@email.com", "31999999999");
        var vehicle = new Vehicle(customer, "ABC1D23", "Fiat", "Argo", 2020);
        return new WorkOrder("OS-TEST-001", customer, vehicle, "Cliente relata barulho.");
    }
}
