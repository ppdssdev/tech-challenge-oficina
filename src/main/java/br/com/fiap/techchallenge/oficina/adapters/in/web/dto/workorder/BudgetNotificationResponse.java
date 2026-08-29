package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

public record BudgetNotificationResponse(
    String workOrderCode,
    String channel,
    String recipient,
    String subject,
    String body,
    String approveUrl,
    String rejectUrl
) {
}
