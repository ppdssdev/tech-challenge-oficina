package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ProcessNotificationOutboxUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessNotificationOutboxService implements ProcessNotificationOutboxUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessNotificationOutboxService.class);
    private final NotificationOutboxPort outbox;
    private final NotificationPort notifications;
    private final int batchSize;

    public ProcessNotificationOutboxService(
        NotificationOutboxPort outbox,
        NotificationPort notifications,
        int batchSize
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Tamanho do lote da outbox deve ser positivo.");
        }
        this.outbox = outbox;
        this.notifications = notifications;
        this.batchSize = batchSize;
    }

    @Override
    public ProcessNotificationOutboxResult processPending() {
        var messages = outbox.findPending(batchSize);
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
                outbox.markFailed(message.id(), errorMessage);
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
