package br.com.fiap.techchallenge.oficina.adapters.out.observability;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.*;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import br.com.fiap.techchallenge.oficina.application.usecase.ProcessNotificationOutboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ObservedNotificationProcessingTest {
    @Test
    void countsActualSendFailuresRetriesAndBatchFailuresButNotEmptyOrSuccessfulRuns() {
        var outbox = mock(NotificationOutboxPort.class);
        var sender = mock(NotificationPort.class);
        var registry = new SimpleMeterRegistry();
        var observed = new ObservedNotificationProcessing(
            new ProcessNotificationOutboxService(outbox, sender, 10, 3, 300), registry);
        var message = new NotificationOutboxMessage(UUID.randomUUID(), Type.BUDGET_DECISION,
            Channel.MAILPIT_EMAIL, Status.PROCESSING, "private@example.test", "subject", "secret",
            "OS-PRIVATE", null, null, 1, null, null, null, null);
        when(outbox.claimPending(anyInt(), any())).thenReturn(List.of(message), List.of(message), List.of());
        doThrow(new IllegalStateException("SMTP failure" )).doNothing().when(sender).send(any());
        assertThat(observed.processPending().failed()).isEqualTo(1);
        assertThat(observed.processPending().sent()).isEqualTo(1);
        assertThat(observed.processPending().processed()).isZero();
        verify(outbox).markPending(message.id(), "SMTP failure");
        when(outbox.claimPending(anyInt(), any())).thenThrow(new IllegalStateException("DB failure"));
        assertThatThrownBy(observed::processPending).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get("oficina.notification.processing.failures").tag("stage", "attempt")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("oficina.notification.processing.failures").tag("stage", "batch")
            .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(m -> assertThat(m.getId().getTags())
            .allSatisfy(t -> assertThat(t.getKey()).isEqualTo("stage")));
    }
}
