package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Channel;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.NotificationOutboxMessage;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BudgetNotificationMessageFactory {
    public NotificationOutboxMessage create(
        String workOrderCode,
        String customerName,
        String customerEmail,
        BigDecimal totalAmount,
        String approveUrl,
        String rejectUrl
    ) {
        String subject = "Orçamento da OS " + workOrderCode + " aguardando aprovação";
        String body = """
            Olá, %s.

            O orçamento da sua Ordem de Serviço %s está aguardando decisão.

            Valor total: R$ %s.

            Para aprovar, envie uma requisição POST para:
            %s

            Para recusar, envie uma requisição POST para:
            %s

            Body esperado:
            {
              "document": "seu CPF ou CNPJ"
            }

            Esta mensagem foi enviada pelo ambiente local da oficina.
            """.formatted(
                customerName,
                workOrderCode,
                totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','),
                approveUrl,
                rejectUrl
            );

        return new NotificationOutboxMessage(
            null, Type.BUDGET_DECISION, Channel.MAILPIT_EMAIL, Status.PENDING,
            customerEmail, subject, body, workOrderCode, approveUrl, rejectUrl,
            0, null, null, null, null
        );
    }
}
