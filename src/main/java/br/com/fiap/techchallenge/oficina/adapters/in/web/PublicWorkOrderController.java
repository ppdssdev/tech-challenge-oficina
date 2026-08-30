package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.BudgetDecisionRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/work-orders")
public class PublicWorkOrderController {

    private final GetWorkOrderUseCase service;
    private final ExternalBudgetDecisionUseCase budgetDecision;

    public PublicWorkOrderController(
        GetWorkOrderUseCase service,
        ExternalBudgetDecisionUseCase budgetDecision
    ) {
        this.service = service;
        this.budgetDecision = budgetDecision;
    }

    @GetMapping("/{code}/status")
    @Operation(summary = "Consulta pública de andamento da OS por código e CPF/CNPJ")
    public PublicWorkOrderStatusResponse status(@PathVariable String code, @RequestParam String document) {
        return WebDtoMapper.toPublicResponse(service.getPublicStatus(code, document));
    }

    @PostMapping("/{code}/budget/approve")
    @Operation(summary = "Aprova externamente o orçamento da OS por código e CPF/CNPJ")
    public PublicWorkOrderStatusResponse approveBudget(
        @PathVariable String code,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody BudgetDecisionRequest request
    ) {
        return WebDtoMapper.toPublicResponse(
            budgetDecision.approve(WebDtoMapper.toCommand(code, request, idempotencyKey))
        );
    }

    @PostMapping("/{code}/budget/reject")
    @Operation(summary = "Recusa externamente o orçamento da OS por código e CPF/CNPJ")
    public PublicWorkOrderStatusResponse rejectBudget(
        @PathVariable String code,
        @Valid @RequestBody BudgetDecisionRequest request
    ) {
        return WebDtoMapper.toPublicResponse(budgetDecision.reject(WebDtoMapper.toCommand(code, request)));
    }
}
