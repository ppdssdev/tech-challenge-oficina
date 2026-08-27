package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.time.OffsetDateTime;
import java.util.List;

public final class CalculateWorkOrderMetricsService implements CalculateWorkOrderMetricsUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public CalculateWorkOrderMetricsService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public ExecutionTimeMetrics averageExecutionTime(OffsetDateTime from, OffsetDateTime to) {
        return transactions.required(() -> {
            var minutes = completed(from, to).stream()
                .map(WorkOrder::executionMinutes)
                .filter(value -> value != null && value >= 0)
                .toList();
            if (minutes.isEmpty()) {
                return new ExecutionTimeMetrics(0, null, null);
            }
            double average = minutes.stream().mapToLong(Long::longValue).average().orElse(0);
            return new ExecutionTimeMetrics(minutes.size(), average, average / 60.0);
        });
    }

    private List<WorkOrder> completed(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null) {
            return workOrders.findCompletedForMetricsBetween(from, to);
        }
        if (from != null) {
            return workOrders.findCompletedForMetricsFrom(from);
        }
        if (to != null) {
            return workOrders.findCompletedForMetricsTo(to);
        }
        return workOrders.findCompletedForMetrics();
    }
}
