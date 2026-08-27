package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

public record AverageExecutionTimeResponse(
    long completedWorkOrders,
    Double averageMinutes,
    Double averageHours
) {
}
