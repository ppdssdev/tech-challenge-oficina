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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private WorkOrder sampleOrder() {
        var customer = new Customer("Maria Silva", DocumentType.CPF, "52998224725", "maria@email.com", "31999999999");
        var vehicle = new Vehicle(customer, "ABC1D23", "Fiat", "Argo", 2020);
        return new WorkOrder("OS-TEST-001", customer, vehicle, "Cliente relata barulho.");
    }
}
