package br.com.fiap.techchallenge.oficina.adapters.out.observability;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static br.com.fiap.techchallenge.oficina.adapters.out.observability.BusinessMetricsReader.*;

@Component
public class BusinessMetrics {
    private final BusinessMetricsReader reader;
    private final Clock clock;
    private final Counter refreshFailures;
    private volatile Snapshot snapshot;

    @Autowired
    public BusinessMetrics(MeterRegistry registry, BusinessMetricsReader reader) {
        this(registry, reader, Clock.systemUTC());
    }

    BusinessMetrics(MeterRegistry registry, BusinessMetricsReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
        refreshFailures = registry.counter("oficina.business.metrics.refresh.failures");
        Gauge.builder("oficina.business.metrics.snapshot.timestamp.seconds", this,
            metrics -> metrics.snapshot == null ? Double.NaN : metrics.snapshot.sampledAt().getEpochSecond())
            .register(registry);
        Gauge.builder("oficina.work.orders.created.today", this, metrics -> metrics.value(s ->
            s.day().equals(clock.instant().atZone(BUSINESS_ZONE).toLocalDate()) ? s.created() : Double.NaN))
            .register(registry);
        for (var status : WorkOrderStatus.values()) {
            register(registry, "oficina.work.order.status.completed.intervals", status,
                s -> s.completed().getOrDefault(status, Interval.ZERO).count());
            register(registry, "oficina.work.order.status.completed.duration.seconds", status,
                s -> s.completed().getOrDefault(status, Interval.ZERO).seconds());
            register(registry, "oficina.work.order.status.open.intervals", status,
                s -> s.open().getOrDefault(status, Interval.ZERO).count());
            register(registry, "oficina.work.order.status.open.age.seconds", status,
                s -> s.open().getOrDefault(status, Interval.ZERO).seconds());
        }
        // Preserve existing names; all gauges now read the same bounded snapshot, never the DB.
        for (var status : Status.values()) {
            Gauge.builder("oficina.notification.outbox." + status.name().toLowerCase(Locale.ROOT), this,
                metrics -> metrics.value(s -> s.outbox().getOrDefault(status, 0L))).register(registry);
        }
    }

    private void register(MeterRegistry registry, String name, WorkOrderStatus status,
                          ToDoubleFunction<Snapshot> value) {
        Gauge.builder(name, this, metrics -> metrics.value(value)).tag("status", status.name()).register(registry);
    }

    private double value(ToDoubleFunction<Snapshot> value) {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current == null || Duration.between(current.sampledAt(), now).compareTo(Duration.ofSeconds(120)) > 0) {
            return Double.NaN;
        }
        return value.applyAsDouble(current);
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 60000, scheduler = "businessMetricsScheduler")
    public void refresh() {
        try {
            snapshot = reader.read(clock.instant());
        } catch (RuntimeException ex) {
            refreshFailures.increment();
            LoggerFactory.getLogger(BusinessMetrics.class).warn("Business metrics refresh failed; cached snapshot expires after 120s");
        }
    }
}
