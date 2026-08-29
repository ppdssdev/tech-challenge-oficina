package br.com.fiap.techchallenge.oficina.application;

import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase.Command;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.ExternalBudgetDecisionService;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalBudgetDecisionServiceTest {

    @Mock
    WorkOrderRepositoryPort workOrderRepository;

    @Mock
    PartRepositoryPort partRepository;

    private final TransactionPort transactions = TransactionPort.direct();

    @Test
    void shouldApproveBudgetForMatchingDocumentAndPersistPartsAndOrder() {
        var fixture = pendingOrder();
        when(workOrderRepository.findByCode(fixture.order().getCode())).thenReturn(Optional.of(fixture.order()));
        when(workOrderRepository.findDetailedByIdForStockUpdate(fixture.order().getId()))
            .thenReturn(Optional.of(fixture.order()));
        when(workOrderRepository.save(fixture.order())).thenReturn(fixture.order());
        var useCase = new ExternalBudgetDecisionService(workOrderRepository, partRepository, transactions);

        var result = useCase.approve(new Command(fixture.order().getCode(), "529.982.247-25"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.IN_EXECUTION);
        assertThat(fixture.part().getQuantityInStock()).isEqualTo(3);
        assertThat(fixture.order().getPartItems().getFirst().isStockReserved()).isTrue();
        verify(partRepository).save(fixture.part());
        verify(workOrderRepository).save(fixture.order());
    }

    @Test
    void shouldRejectBudgetForMatchingDocumentWithoutChangingStock() {
        var fixture = pendingOrder();
        when(workOrderRepository.findByCode(fixture.order().getCode())).thenReturn(Optional.of(fixture.order()));
        when(workOrderRepository.findDetailedByIdForStockUpdate(fixture.order().getId()))
            .thenReturn(Optional.of(fixture.order()));
        when(workOrderRepository.save(fixture.order())).thenReturn(fixture.order());
        var useCase = new ExternalBudgetDecisionService(workOrderRepository, partRepository, transactions);

        var result = useCase.reject(new Command(fixture.order().getCode(), "52998224725"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.BUDGET_REJECTED);
        assertThat(fixture.part().getQuantityInStock()).isEqualTo(5);
        assertThat(fixture.order().getPartItems().getFirst().isStockReserved()).isFalse();
        verify(workOrderRepository).save(fixture.order());
        verifyNoInteractions(partRepository);
    }

    @Test
    void shouldHideWorkOrderWhenDocumentDoesNotMatch() {
        var fixture = pendingOrder();
        when(workOrderRepository.findByCode(fixture.order().getCode())).thenReturn(Optional.of(fixture.order()));
        var useCase = new ExternalBudgetDecisionService(workOrderRepository, partRepository, transactions);

        assertThatThrownBy(() -> useCase.approve(new Command(fixture.order().getCode(), "00000000000")))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("documento informado");

        verify(workOrderRepository, never()).findDetailedByIdForStockUpdate(fixture.order().getId());
        verify(workOrderRepository, never()).save(fixture.order());
        verifyNoInteractions(partRepository);
    }

    private OrderFixture pendingOrder() {
        var createdAt = OffsetDateTime.parse("2026-08-01T10:00:00-03:00");
        var customer = Customer.restore(
            UUID.randomUUID(), "Maria Silva", DocumentType.CPF, "52998224725",
            "maria@email.com", "31999999999", createdAt, createdAt
        );
        var vehicle = Vehicle.restore(
            UUID.randomUUID(), customer, "ABC1D23", "Fiat", "Argo", 2020, createdAt, createdAt
        );
        var part = Part.restore(
            UUID.randomUUID(), "Filtro", "FILTER-001", BigDecimal.valueOf(35),
            5, 1, true, 0, createdAt, createdAt
        );
        var order = new WorkOrder("OS-EXTERNAL-001", customer, vehicle, "Troca preventiva.");
        order.restoreMetadata(UUID.randomUUID(), createdAt, createdAt);
        order.addRequiredPart(part, 2);
        return new OrderFixture(order, part);
    }

    private record OrderFixture(WorkOrder order, Part part) {
    }
}
