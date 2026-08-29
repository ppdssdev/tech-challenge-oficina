package br.com.fiap.techchallenge.oficina.application.port.out;

import java.math.BigDecimal;

public interface NotificationPort {
    NotificationDeliveryResult sendBudgetDecisionNotification(BudgetDecisionNotification notification);

    record BudgetDecisionNotification(
        String workOrderCode,
        String customerName,
        String customerEmail,
        BigDecimal totalAmount,
        String approveUrl,
        String rejectUrl
    ) {
    }

    record NotificationDeliveryResult(
        String channel,
        String recipient,
        String subject,
        String body,
        String approveUrl,
        String rejectUrl
    ) {
    }
}
