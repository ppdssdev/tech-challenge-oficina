package br.com.fiap.techchallenge.oficina.adapters.out.notification;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatedEmailNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailNotificationAdapter.class);
    private static final String CHANNEL = "SIMULATED_EMAIL";

    @Override
    public NotificationDeliveryResult sendBudgetDecisionNotification(BudgetDecisionNotification notification) {
        String subject = "Orçamento da OS " + notification.workOrderCode() + " aguardando aprovação";
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

            Esta mensagem foi gerada por uma integração simulada local.
            """.formatted(
                notification.customerName(),
                notification.workOrderCode(),
                formatAmount(notification.totalAmount()),
                notification.approveUrl(),
                notification.rejectUrl()
            );

        log.info(
            "E-mail simulado de orçamento. destinatario={} assunto={} mensagem=\n{}",
            notification.customerEmail(), subject, body
        );

        return new NotificationDeliveryResult(
            CHANNEL, notification.customerEmail(), subject, body,
            notification.approveUrl(), notification.rejectUrl()
        );
    }

    private String formatAmount(java.math.BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
