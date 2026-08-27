package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PartRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 40) String sku,
    @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
    @Min(0) int quantityInStock,
    @Min(0) int minimumStock,
    boolean active
) {
}
