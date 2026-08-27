package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle;

import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleResponse(
    UUID id,
    UUID customerId,
    String customerName,
    String customerDocumentNumber,
    String plate,
    String brand,
    String model,
    int manufacturingYear,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getCustomer().getId(),
            vehicle.getCustomer().getFullName(),
            vehicle.getCustomer().getDocumentNumber(),
            vehicle.getPlate(),
            vehicle.getBrand(),
            vehicle.getModel(),
            vehicle.getManufacturingYear(),
            vehicle.getCreatedAt(),
            vehicle.getUpdatedAt()
        );
    }
}
