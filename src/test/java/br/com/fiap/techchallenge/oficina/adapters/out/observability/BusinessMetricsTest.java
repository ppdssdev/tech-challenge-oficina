package br.com.fiap.techchallenge.oficina.adapters.out.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BusinessMetricsTest {
    @Test
    void scrapesOnlyReadCacheAndFailedRefreshExpiresWithoutInventingZero() {
        var now = Instant.parse("2026-11-03T15:00:00Z");
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        var reader = mock(BusinessMetricsReader.class);
        var registry = new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        var metrics = new BusinessMetrics(registry, reader, clock);
        var gauge = registry.get("oficina.work.orders.created.today").gauge();
        assertThat(gauge.value()).isNaN();
        when(reader.read(now)).thenReturn(new BusinessMetricsReader.Snapshot(now,
            LocalDate.of(2026, 11, 3), 4, Map.of(), Map.of(), Map.of()));
        metrics.refresh();
        for (int i = 0; i < 20; i++) {
            assertThat(gauge.value()).isEqualTo(4);
            assertThat(registry.scrape()).contains(
                "# TYPE oficina_work_orders_created_today gauge",
                "oficina_work_orders_created_today 4.0",
                "# TYPE oficina_work_order_status_completed_duration_seconds gauge",
                "# TYPE oficina_notification_outbox_pending gauge",
                "# TYPE oficina_business_metrics_refresh_failures_total counter");
        }
        verify(reader, times(1)).read(now);
        assertThat(registry.get("oficina.notification.outbox.pending").gauge().value()).isZero();
        assertThat(registry.get("oficina.work.order.status.completed.intervals")
            .tag("status", "RECEIVED").gauge().value()).isZero();
        when(reader.read(now)).thenThrow(new IllegalStateException("private data"));
        metrics.refresh();
        assertThat(gauge.value()).isEqualTo(4);
        assertThat(registry.get("oficina.business.metrics.refresh.failures").counter().count()).isEqualTo(1);
        when(clock.instant()).thenReturn(now.plusSeconds(121));
        assertThat(gauge.value()).isNaN();
        assertThat(registry.get("oficina.notification.outbox.pending").gauge().value()).isNaN();
        assertThat(registry.get("oficina.business.metrics.snapshot.timestamp.seconds").gauge().value())
            .isEqualTo(now.getEpochSecond());
        assertThat(registry.getMeters()).allSatisfy(m -> assertThat(m.getId().getTags())
            .allSatisfy(t -> assertThat(t.getKey()).isEqualTo("status")));
    }

    @Test
    void doesNotCarryYesterdayCountAcrossSaoPauloMidnight() {
        var now = Instant.parse("2026-11-04T02:59:59Z");
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        var reader = mock(BusinessMetricsReader.class);
        when(reader.read(now)).thenReturn(new BusinessMetricsReader.Snapshot(now,
            LocalDate.of(2026, 11, 3), 9, Map.of(), Map.of(), Map.of()));
        var registry = new SimpleMeterRegistry();
        var metrics = new BusinessMetrics(registry, reader, clock);
        metrics.refresh();
        assertThat(registry.get("oficina.work.orders.created.today").gauge().value()).isEqualTo(9);
        when(clock.instant()).thenReturn(now.plusSeconds(1));
        assertThat(registry.get("oficina.work.orders.created.today").gauge().value()).isNaN();
    }
}
