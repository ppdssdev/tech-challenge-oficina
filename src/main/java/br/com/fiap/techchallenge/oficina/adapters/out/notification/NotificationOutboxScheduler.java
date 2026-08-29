package br.com.fiap.techchallenge.oficina.adapters.out.notification;

import br.com.fiap.techchallenge.oficina.application.port.in.ProcessNotificationOutboxUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.notification.outbox.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class NotificationOutboxScheduler {
    private final ProcessNotificationOutboxUseCase processOutbox;

    public NotificationOutboxScheduler(ProcessNotificationOutboxUseCase processOutbox) {
        this.processOutbox = processOutbox;
    }

    @Scheduled(fixedDelayString = "${app.notification.outbox.poll-delay-ms:5000}")
    public void processPending() {
        processOutbox.processPending();
    }
}
