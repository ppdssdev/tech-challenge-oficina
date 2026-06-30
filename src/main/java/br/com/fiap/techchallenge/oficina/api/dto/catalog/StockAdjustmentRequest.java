package br.com.fiap.techchallenge.oficina.api.dto.catalog;

import jakarta.validation.constraints.Min;

public record StockAdjustmentRequest(
    @Min(1) int quantity
) {
}
