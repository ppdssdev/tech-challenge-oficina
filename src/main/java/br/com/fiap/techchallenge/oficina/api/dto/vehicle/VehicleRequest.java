package br.com.fiap.techchallenge.oficina.api.dto.vehicle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VehicleRequest(
    @NotNull UUID customerId,
    @NotBlank @Size(max = 8) String plate,
    @NotBlank @Size(max = 60) String brand,
    @NotBlank @Size(max = 80) String model,
    @Min(1900) @Max(2100) int manufacturingYear
) {
}
