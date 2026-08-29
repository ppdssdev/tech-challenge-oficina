package br.com.fiap.techchallenge.oficina.application;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.NotificationOutboxMessage;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.NotifyBudgetService;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyBudgetServiceTest {

    @Mock
    WorkOrderRepositoryPort workOrderRepository;

    @Mock
    NotificationOutboxPort outboxPort;

    private final TransactionPort transactions = TransactionPort.direct();

    @Test
    void shouldEnqueuePendingMailpitNotificationWithoutChangingWorkOrderStatus() {
        UUID id = UUID.randomUUID();
        var order = waitingApprovalOrder("maria@email.com");
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.of(order));
        when(outboxPort.enqueueBudgetDecision(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new NotifyBudgetService(
            workOrderRepository, outboxPort, transactions, "http://localhost:8080/"
        );

        var result = useCase.notifyBudget(id);

        assertThat(result.channel()).isEqualTo("MAILPIT_EMAIL");
        assertThat(result.recipient()).isEqualTo("maria@email.com");
        assertThat(result.subject()).contains("OS-NOTIFY-001");
        assertThat(result.body()).contains("R$ 120,00");
        assertThat(result.approveUrl()).isEqualTo(
            "http://localhost:8080/api/v1/public/work-orders/OS-NOTIFY-001/budget/approve"
        );
        assertThat(result.rejectUrl()).isEqualTo(
            "http://localhost:8080/api/v1/public/work-orders/OS-NOTIFY-001/budget/reject"
        );
        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WAITING_APPROVAL);
        verify(workOrderRepository, never()).save(any());

        var messageCaptor = ArgumentCaptor.forClass(NotificationOutboxMessage.class);
        verify(outboxPort).enqueueBudgetDecision(messageCaptor.capture());
        assertThat(messageCaptor.getValue().status()).isEqualTo(NotificationOutboxPort.Status.PENDING);
        assertThat(messageCaptor.getValue().channel()).isEqualTo(NotificationOutboxPort.Channel.MAILPIT_EMAIL);
        assertThat(messageCaptor.getValue().workOrderCode()).isEqualTo("OS-NOTIFY-001");
    }

    @Test
    void shouldRejectNotificationWhenWorkOrderIsNotWaitingForApproval() {
        UUID id = UUID.randomUUID();
        var order = receivedOrder("maria@email.com");
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.of(order));
        var useCase = new NotifyBudgetService(
            workOrderRepository, outboxPort, transactions, "http://localhost:8080"
        );

        assertThatThrownBy(() -> useCase.notifyBudget(id))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("aguardando aprovação");

        verifyNoInteractions(outboxPort);
    }

    @Test
    void shouldRejectNotificationWhenWorkOrderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.empty());
        var useCase = new NotifyBudgetService(
            workOrderRepository, outboxPort, transactions, "http://localhost:8080"
        );

        assertThatThrownBy(() -> useCase.notifyBudget(id))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Ordem de serviço não encontrada");

        verifyNoInteractions(outboxPort);
    }

    @Test
    void shouldRejectNotificationWhenCustomerHasNoEmail() {
        UUID id = UUID.randomUUID();
        var order = waitingApprovalOrder(null);
        when(workOrderRepository.findDetailedById(id)).thenReturn(Optional.of(order));
        var useCase = new NotifyBudgetService(
            workOrderRepository, outboxPort, transactions, "http://localhost:8080"
        );

        assertThatThrownBy(() -> useCase.notifyBudget(id))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não possui e-mail");

        verifyNoInteractions(outboxPort);
    }

    private WorkOrder waitingApprovalOrder(String email) {
        var order = receivedOrder(email);
        order.addRequestedService(
            new ServiceCatalogItem("Troca de óleo", "Troca preventiva", BigDecimal.valueOf(120), 40), 1
        );
        return order;
    }

    private WorkOrder receivedOrder(String email) {
        var customer = new Customer(
            "Maria Silva", DocumentType.CPF, "52998224725", email, "31999999999"
        );
        var vehicle = new Vehicle(customer, "ABC1D23", "Fiat", "Argo", 2020);
        return new WorkOrder("OS-NOTIFY-001", customer, vehicle, "Troca preventiva.");
    }
}
