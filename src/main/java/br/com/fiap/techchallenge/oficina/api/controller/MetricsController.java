package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.workorder.AverageExecutionTimeResponse;
import br.com.fiap.techchallenge.oficina.application.service.WorkOrderApplicationService;
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

    private final WorkOrderApplicationService service;

    public MetricsController(WorkOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping("/average-execution-time")
    @Operation(summary = "Calcula tempo médio de execução de OS finalizadas")
    public AverageExecutionTimeResponse averageExecutionTime(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return service.averageExecutionTime(from, to);
    }
}
