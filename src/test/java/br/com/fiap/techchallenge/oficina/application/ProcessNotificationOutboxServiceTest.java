package br.com.fiap.techchallenge.oficina.application;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Channel;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.NotificationOutboxMessage;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Type;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import br.com.fiap.techchallenge.oficina.application.usecase.ProcessNotificationOutboxService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessNotificationOutboxServiceTest {
    @Mock NotificationOutboxPort outbox;
    @Mock NotificationPort notifications;

    @Test
    void shouldSendPendingMessageAndMarkItAsSent() {
        var message = pending("cliente1@email.com");
        when(outbox.claimPending(anyInt(), any())).thenReturn(List.of(message));
        var service = new ProcessNotificationOutboxService(outbox, notifications, 10, 3, 300);

        var result = service.processPending();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        InOrder order = inOrder(notifications, outbox);
        order.verify(notifications).send(any());
        order.verify(outbox).markSent(message.id());
    }

    @Test
    void shouldMarkFailureAndContinueProcessingTheBatch() {
        var failedMessage = pending("falha@email.com");
        var sentMessage = pending("sucesso@email.com");
        when(outbox.claimPending(anyInt(), any())).thenReturn(List.of(failedMessage, sentMessage));
        doThrow(new IllegalStateException("SMTP indisponível"))
            .doNothing()
            .when(notifications).send(any());
        var service = new ProcessNotificationOutboxService(outbox, notifications, 10, 3, 300);

        var result = service.processPending();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(outbox).markPending(failedMessage.id(), "SMTP indisponível");
        verify(outbox).markSent(sentMessage.id());
    }

    @Test
    void shouldMarkFailureAsFinalAfterMaximumAttempts() {
        var message = processing("falha@email.com", 3);
        when(outbox.claimPending(anyInt(), any())).thenReturn(List.of(message));
        doThrow(new IllegalStateException("SMTP indisponível")).when(notifications).send(any());
        var service = new ProcessNotificationOutboxService(outbox, notifications, 10, 3, 300);

        var result = service.processPending();

        assertThat(result.failed()).isEqualTo(1);
        verify(outbox).markFailed(message.id(), "SMTP indisponível");
    }

    private NotificationOutboxMessage pending(String recipient) {
        return processing(recipient, 1);
    }

    private NotificationOutboxMessage processing(String recipient, int attempts) {
        return new NotificationOutboxMessage(
            UUID.randomUUID(), Type.BUDGET_DECISION, Channel.MAILPIT_EMAIL, Status.PROCESSING,
            recipient, "Orçamento", "Corpo", "OS-001", "approve", "reject",
            attempts, null, null, null, null
        );
    }
}
