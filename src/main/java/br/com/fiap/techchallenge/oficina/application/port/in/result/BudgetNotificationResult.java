package br.com.fiap.techchallenge.oficina.application.port.in.result;

public record BudgetNotificationResult(
    String workOrderCode,
    String channel,
    String recipient,
    String subject,
    String body,
    String approveUrl,
    String rejectUrl
) {
}
