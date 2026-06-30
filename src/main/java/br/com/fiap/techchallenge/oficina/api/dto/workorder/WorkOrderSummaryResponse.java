package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderSummaryResponse(
    UUID id,
    String code,
    WorkOrderStatus status,
    String statusLabel,
    String customerName,
    String customerDocumentNumber,
    String vehiclePlate,
    String vehicleDescription,
    BigDecimal totalAmount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static WorkOrderSummaryResponse from(WorkOrder order) {
        return new WorkOrderSummaryResponse(
            order.getId(),
            order.getCode(),
            order.getStatus(),
            order.getStatus().getLabel(),
            order.getCustomer().getFullName(),
            order.getCustomer().getDocumentNumber(),
            order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
