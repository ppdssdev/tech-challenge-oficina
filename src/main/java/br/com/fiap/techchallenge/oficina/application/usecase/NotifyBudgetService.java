package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.NotifyBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.BudgetNotificationResult;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort.BudgetDecisionNotification;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.util.UUID;

public final class NotifyBudgetService implements NotifyBudgetUseCase {

    private final WorkOrderRepositoryPort workOrders;
    private final NotificationPort notifications;
    private final TransactionPort transactions;
    private final String publicBaseUrl;

    public NotifyBudgetService(
        WorkOrderRepositoryPort workOrders,
        NotificationPort notifications,
        TransactionPort transactions,
        String publicBaseUrl
    ) {
        this.workOrders = workOrders;
        this.notifications = notifications;
        this.transactions = transactions;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
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
            var delivery = notifications.sendBudgetDecisionNotification(new BudgetDecisionNotification(
                order.getCode(), customer.getFullName(), customer.getEmail(), order.getTotalAmount(),
                publicBaseUrl + decisionPath + "approve", publicBaseUrl + decisionPath + "reject"
            ));

            return new BudgetNotificationResult(
                order.getCode(), delivery.channel(), delivery.recipient(), delivery.subject(), delivery.body(),
                delivery.approveUrl(), delivery.rejectUrl()
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
