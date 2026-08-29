package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.NotifyBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.BudgetNotificationResult;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.util.UUID;

public final class NotifyBudgetService implements NotifyBudgetUseCase {

    private final WorkOrderRepositoryPort workOrders;
    private final NotificationOutboxPort outbox;
    private final TransactionPort transactions;
    private final String publicBaseUrl;
    private final BudgetNotificationMessageFactory messageFactory;

    public NotifyBudgetService(
        WorkOrderRepositoryPort workOrders,
        NotificationOutboxPort outbox,
        TransactionPort transactions,
        String publicBaseUrl
    ) {
        this.workOrders = workOrders;
        this.outbox = outbox;
        this.transactions = transactions;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.messageFactory = new BudgetNotificationMessageFactory();
    }

    @Override
    public BudgetNotificationResult notifyBudget(UUID workOrderId) {
        return transactions.required(() -> {
            var order = workOrders.findDetailedById(workOrderId)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            if (order.getStatus() != WorkOrderStatus.WAITING_APPROVAL) {
                throw new BusinessException("Somente OS aguardando aprovação pode ter o orçamento notificado.");
            }

            var customer = order.getCustomer();
            if (customer.getEmail() == null || customer.getEmail().isBlank()) {
                throw new BusinessException("Cliente da OS não possui e-mail cadastrado.");
            }

            String decisionPath = "/api/v1/public/work-orders/" + order.getCode() + "/budget/";
            var message = messageFactory.create(
                order.getCode(), customer.getFullName(), customer.getEmail(), order.getTotalAmount(),
                publicBaseUrl + decisionPath + "approve", publicBaseUrl + decisionPath + "reject"
            );
            var queued = outbox.enqueueBudgetDecision(message);

            return new BudgetNotificationResult(
                order.getCode(), queued.channel().name(), queued.recipient(), queued.subject(), queued.body(),
                queued.approveUrl(), queued.rejectUrl()
            );
        });
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("URL base pública é obrigatória.");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
