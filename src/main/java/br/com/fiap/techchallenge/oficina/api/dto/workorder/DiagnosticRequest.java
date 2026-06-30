package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import jakarta.validation.constraints.Size;

public record DiagnosticRequest(
    @Size(max = 2000) String notes
) {
}
