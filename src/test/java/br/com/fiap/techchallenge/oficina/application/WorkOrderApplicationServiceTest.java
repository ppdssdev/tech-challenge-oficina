package br.com.fiap.techchallenge.oficina.application;

import br.com.fiap.techchallenge.oficina.api.dto.workorder.AddWorkOrderItemsRequest;
import br.com.fiap.techchallenge.oficina.application.service.WorkOrderApplicationService;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.CustomerRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.PartRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.ServiceCatalogRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.VehicleRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.WorkOrderRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderApplicationServiceTest {

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    CustomerRepository customerRepository;

    @Mock
    VehicleRepository vehicleRepository;

    @Mock
    ServiceCatalogRepository serviceCatalogRepository;

    @Mock
    PartRepository partRepository;

    WorkOrderApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderApplicationService(
            workOrderRepository,
            customerRepository,
            vehicleRepository,
            serviceCatalogRepository,
            partRepository
        );
    }

    @Test
    void shouldRejectApproveBudgetWhenWorkOrderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveBudget(id))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Ordem de serviço não encontrada");
    }

    @Test
    void shouldRejectAddItemsWhenRequestHasNoItems() {
        UUID id = UUID.randomUUID();
        var order = sampleOrder();
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.of(order));
        when(workOrderRepository.findDetailedServiceItemsById(id)).thenReturn(Optional.of(order));
        when(workOrderRepository.findDetailedPartItemsById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.addItems(id, new AddWorkOrderItemsRequest(List.of(), List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Informe ao menos um serviço ou peça");
    }

    @Test
    void shouldHidePublicStatusWhenDocumentDoesNotMatch() {
        var order = sampleOrder();
        when(workOrderRepository.findByCode(order.getCode())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.publicStatus(order.getCode(), "00000000000"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Ordem de serviço não encontrada");
    }

    @Test
    void shouldQueryAverageExecutionTimeWithoutDateFilters() {
        when(workOrderRepository.findCompletedForMetrics()).thenReturn(List.of());

        var response = service.averageExecutionTime(null, null);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetrics();
    }

    @Test
    void shouldQueryAverageExecutionTimeWithFromFilterOnly() {
        var from = OffsetDateTime.parse("2026-06-30T00:00:00-03:00");
        when(workOrderRepository.findCompletedForMetricsFrom(from))
            .thenReturn(List.of());

        var response = service.averageExecutionTime(from, null);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetricsFrom(from);
    }

    @Test
    void shouldQueryAverageExecutionTimeWithToFilterOnly() {
        var to = OffsetDateTime.parse("2026-06-30T23:59:59-03:00");
        when(workOrderRepository.findCompletedForMetricsTo(to))
            .thenReturn(List.of());

        var response = service.averageExecutionTime(null, to);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetricsTo(to);
    }

    @Test
    void shouldQueryAverageExecutionTimeWithDateRange() {
        var from = OffsetDateTime.parse("2026-06-30T00:00:00-03:00");
        var to = OffsetDateTime.parse("2026-06-30T23:59:59-03:00");
        when(workOrderRepository.findCompletedForMetricsBetween(from, to))
            .thenReturn(List.of());

        var response = service.averageExecutionTime(from, to);

        assertThat(response.completedWorkOrders()).isZero();
        verify(workOrderRepository).findCompletedForMetricsBetween(from, to);
    }

    private WorkOrder sampleOrder() {
        var customer = new Customer("Maria Silva", DocumentType.CPF, "52998224725", "maria@email.com", "31999999999");
        var vehicle = new Vehicle(customer, "ABC1D23", "Fiat", "Argo", 2020);
        return new WorkOrder("OS-TEST-001", customer, vehicle, "Cliente relata barulho.");
    }
}
