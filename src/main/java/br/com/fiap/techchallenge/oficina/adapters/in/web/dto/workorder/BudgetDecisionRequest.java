package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

import jakarta.validation.constraints.NotBlank;

public record BudgetDecisionRequest(
    @NotBlank String document
) {
}
