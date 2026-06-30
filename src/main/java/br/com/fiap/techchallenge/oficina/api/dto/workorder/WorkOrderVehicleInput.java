package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkOrderVehicleInput(
    @NotBlank @Size(max = 8) String plate,
    @NotBlank @Size(max = 60) String brand,
    @NotBlank @Size(max = 80) String model,
    @Min(1900) @Max(2100) int manufacturingYear
) {
}
