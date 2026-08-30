package br.com.fiap.techchallenge.oficina.adapters.out.observability;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxMetrics {

    public NotificationOutboxMetrics(MeterRegistry meterRegistry, NotificationOutboxPort outbox) {
        registerGauge(meterRegistry, outbox, "pending", Status.PENDING);
        registerGauge(meterRegistry, outbox, "processing", Status.PROCESSING);
        registerGauge(meterRegistry, outbox, "sent", Status.SENT);
        registerGauge(meterRegistry, outbox, "failed", Status.FAILED);
    }

    private static void registerGauge(
        MeterRegistry meterRegistry,
        NotificationOutboxPort outbox,
        String metricStatus,
        Status status
    ) {
        meterRegistry.gauge(
            "oficina.notification.outbox." + metricStatus,
            outbox,
            port -> port.countByStatus(status)
        );
    }
}
