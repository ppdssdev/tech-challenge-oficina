package br.com.fiap.techchallenge.oficina.adapters.out.observability;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Aggregate rows only: never load orders, customers, payloads or full history into memory. */
@Component
public class BusinessMetricsReader {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private final JdbcTemplate jdbc;

    public BusinessMetricsReader(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(2);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 2)
    public Snapshot read(Instant now) {
        LocalDate day = now.atZone(BUSINESS_ZONE).toLocalDate();
        var start = day.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        var end = day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        var at = now.atOffset(ZoneOffset.UTC);
        long created = jdbc.queryForObject(
            "select count(*) from work_orders where created_at >= ? and created_at < ?",
            Long.class, start, end);
        var completed = new EnumMap<WorkOrderStatus, Interval>(WorkOrderStatus.class);
        jdbc.query("""
            select h.new_status, count(*) as intervals,
                   sum(extract(epoch from (n.occurred_at - h.occurred_at))) as seconds
            from work_order_status_history h
            join work_order_status_history n on n.work_order_id = h.work_order_id
                 and n.event_order = h.event_order + 1 and n.previous_status = h.new_status
            where n.occurred_at >= h.occurred_at and n.occurred_at <= ?
            group by h.new_status
            """, rs -> {
                completed.put(WorkOrderStatus.valueOf(rs.getString("new_status")),
                    new Interval(rs.getLong("intervals"), rs.getDouble("seconds")));
            }, at);
        var open = new EnumMap<WorkOrderStatus, Interval>(WorkOrderStatus.class);
        jdbc.query("""
            select h.new_status, count(*) as intervals,
                   sum(extract(epoch from (? - h.occurred_at))) as seconds
            from work_order_status_history h
            join work_orders w on w.id = h.work_order_id and w.status = h.new_status
            where h.occurred_at <= ? and h.new_status not in ('DELIVERED', 'BUDGET_REJECTED')
              and not exists (select 1 from work_order_status_history n
                              where n.work_order_id = h.work_order_id and n.event_order > h.event_order)
            group by h.new_status
            """, rs -> {
                open.put(WorkOrderStatus.valueOf(rs.getString("new_status")),
                    new Interval(rs.getLong("intervals"), rs.getDouble("seconds")));
            }, at, at);
        var outbox = new EnumMap<Status, Long>(Status.class);
        jdbc.query("select status, count(*) as messages from notification_outbox group by status", rs -> {
            outbox.put(Status.valueOf(rs.getString("status")), rs.getLong("messages"));
        });
        return new Snapshot(now, day, created, Map.copyOf(completed), Map.copyOf(open), Map.copyOf(outbox));
    }

    public record Interval(long count, double seconds) {
        static final Interval ZERO = new Interval(0, 0);
    }
    public record Snapshot(Instant sampledAt, LocalDate day, long created,
                           Map<WorkOrderStatus, Interval> completed,
                           Map<WorkOrderStatus, Interval> open, Map<Status, Long> outbox) { }
}
