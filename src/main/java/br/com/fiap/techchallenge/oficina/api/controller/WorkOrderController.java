package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.workorder.AddWorkOrderItemsRequest;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.CreateWorkOrderRequest;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.DiagnosticRequest;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.WorkOrderResponse;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.WorkOrderSummaryResponse;
import br.com.fiap.techchallenge.oficina.application.service.WorkOrderApplicationService;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/work-orders")
public class WorkOrderController {

    private final WorkOrderApplicationService service;

    public WorkOrderController(WorkOrderApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria Ordem de Serviço com cliente, veículo, serviços, peças e orçamento automático")
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/work-orders/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista Ordens de Serviço")
    public List<WorkOrderSummaryResponse> list(@RequestParam(required = false) WorkOrderStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha Ordem de Serviço")
    public WorkOrderResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/diagnosis/start")
    @Operation(summary = "Move OS recebida para diagnóstico")
    public WorkOrderResponse startDiagnosis(@PathVariable UUID id, @Valid @RequestBody DiagnosticRequest request) {
        return service.startDiagnosis(id, request.notes());
    }

    @PostMapping("/{id}/diagnosis/notes")
    @Operation(summary = "Atualiza notas de diagnóstico")
    public WorkOrderResponse updateDiagnosticNotes(@PathVariable UUID id, @Valid @RequestBody DiagnosticRequest request) {
        return service.updateDiagnosticNotes(id, request.notes());
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Inclui serviços, peças ou insumos adicionais e envia orçamento para aprovação")
    public WorkOrderResponse addItems(@PathVariable UUID id, @Valid @RequestBody AddWorkOrderItemsRequest request) {
        return service.addItems(id, request);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Aprova orçamento, baixa estoque pendente e move OS para execução")
    public WorkOrderResponse approveBudget(@PathVariable UUID id) {
        return service.approveBudget(id);
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Finaliza OS em execução")
    public WorkOrderResponse finish(@PathVariable UUID id) {
        return service.finish(id);
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "Entrega OS finalizada ao cliente")
    public WorkOrderResponse deliver(@PathVariable UUID id) {
        return service.deliver(id);
    }
}
