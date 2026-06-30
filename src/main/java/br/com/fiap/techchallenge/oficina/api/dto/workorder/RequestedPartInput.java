package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequestedPartInput(
    @NotNull UUID partId,
    @Min(1) int quantity
) {
}
