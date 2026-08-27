package br.com.fiap.techchallenge.oficina.application.port.in;

import java.time.OffsetDateTime;

public interface CalculateWorkOrderMetricsUseCase {
    ExecutionTimeMetrics averageExecutionTime(OffsetDateTime from, OffsetDateTime to);

    record ExecutionTimeMetrics(long completedWorkOrders, Double averageMinutes, Double averageHours) {
    }
}
