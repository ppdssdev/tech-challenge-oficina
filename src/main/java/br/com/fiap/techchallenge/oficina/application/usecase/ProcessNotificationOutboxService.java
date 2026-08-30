package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ProcessNotificationOutboxUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;

public final class ProcessNotificationOutboxService implements ProcessNotificationOutboxUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessNotificationOutboxService.class);
    private final NotificationOutboxPort outbox;
    private final NotificationPort notifications;
    private final int batchSize;
    private final int maxAttempts;
    private final int processingTimeoutSeconds;

    public ProcessNotificationOutboxService(
        NotificationOutboxPort outbox,
        NotificationPort notifications,
        int batchSize,
        int maxAttempts,
        int processingTimeoutSeconds
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Tamanho do lote da outbox deve ser positivo.");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Número máximo de tentativas da outbox deve ser positivo.");
        }
        if (processingTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Timeout de processamento da outbox deve ser positivo.");
        }
        this.outbox = outbox;
        this.notifications = notifications;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    @Override
    public ProcessNotificationOutboxResult processPending() {
        OffsetDateTime staleBefore = OffsetDateTime.now().minusSeconds(processingTimeoutSeconds);
        var messages = outbox.claimPending(batchSize, staleBefore);
        int sent = 0;
        int failed = 0;
        for (var message : messages) {
            try {
                notifications.send(new NotificationMessage(message.recipient(), message.subject(), message.body()));
                outbox.markSent(message.id());
                sent++;
            } catch (RuntimeException exception) {
                failed++;
                String errorMessage = safeErrorMessage(exception);
                if (message.attempts() < maxAttempts) {
                    outbox.markPending(message.id(), errorMessage);
                } else {
                    outbox.markFailed(message.id(), errorMessage);
                }
                log.warn("Falha ao processar notificação da outbox. id={} erro={}", message.id(), errorMessage);
            }
        }
        return new ProcessNotificationOutboxResult(messages.size(), sent, failed);
    }

    private static String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
