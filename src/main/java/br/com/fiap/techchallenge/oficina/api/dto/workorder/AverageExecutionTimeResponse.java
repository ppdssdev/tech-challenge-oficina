package br.com.fiap.techchallenge.oficina.api.dto.workorder;

public record AverageExecutionTimeResponse(
    long completedWorkOrders,
    Double averageMinutes,
    Double averageHours
) {
}
