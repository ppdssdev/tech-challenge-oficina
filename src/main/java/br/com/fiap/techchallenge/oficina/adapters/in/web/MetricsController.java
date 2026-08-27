package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.AverageExecutionTimeResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/metrics")
public class MetricsController {

    private final CalculateWorkOrderMetricsUseCase service;

    public MetricsController(CalculateWorkOrderMetricsUseCase service) {
        this.service = service;
    }

    @GetMapping("/average-execution-time")
    @Operation(summary = "Calcula tempo médio de execução de OS finalizadas")
    public AverageExecutionTimeResponse averageExecutionTime(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return WebDtoMapper.toResponse(service.averageExecutionTime(from, to));
    }
}
