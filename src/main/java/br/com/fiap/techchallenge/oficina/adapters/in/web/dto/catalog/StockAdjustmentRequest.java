package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog;

import jakarta.validation.constraints.Min;

public record StockAdjustmentRequest(
    @Min(1) int quantity
) {
}
