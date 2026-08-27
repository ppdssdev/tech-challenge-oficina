package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServiceCatalogRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @NotNull @DecimalMin("0.00") BigDecimal basePrice,
    @Min(1) int estimatedMinutes,
    boolean active
) {
}
