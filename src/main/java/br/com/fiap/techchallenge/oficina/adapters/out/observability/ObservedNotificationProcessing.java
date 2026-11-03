package br.com.fiap.techchallenge.oficina.adapters.out.observability;

import br.com.fiap.techchallenge.oficina.application.port.in.ProcessNotificationOutboxUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Keeps Micrometer outside application/domain and covers scheduled and manual processing. */
public final class ObservedNotificationProcessing implements ProcessNotificationOutboxUseCase {
    private final ProcessNotificationOutboxUseCase delegate;
    private final Counter attempts;
    private final Counter batches;

    public ObservedNotificationProcessing(ProcessNotificationOutboxUseCase delegate, MeterRegistry registry) {
        this.delegate = delegate;
        attempts = registry.counter("oficina.notification.processing.failures", "stage", "attempt");
        batches = registry.counter("oficina.notification.processing.failures", "stage", "batch");
    }

    @Override
    public ProcessNotificationOutboxResult processPending() {
        try {
            var result = delegate.processPending();
            attempts.increment(result.failed());
            return result;
        } catch (RuntimeException ex) {
            batches.increment();
            throw ex;
        }
    }
}
