package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.AddWorkOrderItemsRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.CreateWorkOrderRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.DiagnosticRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderSummaryResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CreateWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.DecideBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.DeliverWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.FinishWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ListWorkOrdersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.StartDiagnosisUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.UpdateDiagnosisUseCase;
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

    private final CreateWorkOrderUseCase createWorkOrder;
    private final ListWorkOrdersUseCase listWorkOrders;
    private final GetWorkOrderUseCase getWorkOrder;
    private final StartDiagnosisUseCase startDiagnosis;
    private final UpdateDiagnosisUseCase updateDiagnosis;
    private final AddWorkOrderItemsUseCase addItems;
    private final DecideBudgetUseCase decideBudget;
    private final FinishWorkOrderUseCase finishWorkOrder;
    private final DeliverWorkOrderUseCase deliverWorkOrder;

    public WorkOrderController(
        CreateWorkOrderUseCase createWorkOrder,
        ListWorkOrdersUseCase listWorkOrders,
        GetWorkOrderUseCase getWorkOrder,
        StartDiagnosisUseCase startDiagnosis,
        UpdateDiagnosisUseCase updateDiagnosis,
        AddWorkOrderItemsUseCase addItems,
        DecideBudgetUseCase decideBudget,
        FinishWorkOrderUseCase finishWorkOrder,
        DeliverWorkOrderUseCase deliverWorkOrder
    ) {
        this.createWorkOrder = createWorkOrder;
        this.listWorkOrders = listWorkOrders;
        this.getWorkOrder = getWorkOrder;
        this.startDiagnosis = startDiagnosis;
        this.updateDiagnosis = updateDiagnosis;
        this.addItems = addItems;
        this.decideBudget = decideBudget;
        this.finishWorkOrder = finishWorkOrder;
        this.deliverWorkOrder = deliverWorkOrder;
    }

    @PostMapping
    @Operation(summary = "Cria Ordem de Serviço com cliente, veículo, serviços, peças e orçamento automático")
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        var response = WebDtoMapper.toResponse(createWorkOrder.create(WebDtoMapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/work-orders/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista Ordens de Serviço")
    public List<WorkOrderSummaryResponse> list(@RequestParam(required = false) ListWorkOrdersUseCase.StatusFilter status) {
        return listWorkOrders.list(status).stream().map(WebDtoMapper::toSummaryResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha Ordem de Serviço")
    public WorkOrderResponse detail(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(getWorkOrder.get(id));
    }

    @PostMapping("/{id}/diagnosis/start")
    @Operation(summary = "Move OS recebida para diagnóstico")
    public WorkOrderResponse startDiagnosis(@PathVariable UUID id, @Valid @RequestBody DiagnosticRequest request) {
        return WebDtoMapper.toResponse(startDiagnosis.start(id, request.notes()));
    }

    @PostMapping("/{id}/diagnosis/notes")
    @Operation(summary = "Atualiza notas de diagnóstico")
    public WorkOrderResponse updateDiagnosticNotes(@PathVariable UUID id, @Valid @RequestBody DiagnosticRequest request) {
        return WebDtoMapper.toResponse(updateDiagnosis.update(id, request.notes()));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Inclui serviços, peças ou insumos adicionais e envia orçamento para aprovação")
    public WorkOrderResponse addItems(@PathVariable UUID id, @Valid @RequestBody AddWorkOrderItemsRequest request) {
        return WebDtoMapper.toResponse(addItems.add(id, request));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Aprova orçamento, baixa estoque pendente e move OS para execução")
    public WorkOrderResponse approveBudget(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(decideBudget.approve(id));
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Finaliza OS em execução")
    public WorkOrderResponse finish(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(finishWorkOrder.finish(id));
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "Entrega OS finalizada ao cliente")
    public WorkOrderResponse deliver(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(deliverWorkOrder.deliver(id));
    }
}
