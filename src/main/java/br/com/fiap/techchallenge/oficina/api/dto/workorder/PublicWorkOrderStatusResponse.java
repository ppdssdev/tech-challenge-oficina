package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PublicWorkOrderStatusResponse(
    String code,
    WorkOrderStatus status,
    String statusLabel,
    String customerName,
    String vehiclePlate,
    String vehicleDescription,
    String diagnosticNotes,
    BigDecimal totalAmount,
    OffsetDateTime customerAuthorizedAt,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime deliveredAt,
    OffsetDateTime updatedAt
) {
    public static PublicWorkOrderStatusResponse from(WorkOrder order) {
        return new PublicWorkOrderStatusResponse(
            order.getCode(),
            order.getStatus(),
            order.getStatus().getLabel(),
            order.getCustomer().getFullName(),
            order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel(),
            order.getDiagnosticNotes(),
            order.getTotalAmount(),
            order.getCustomerAuthorizedAt(),
            order.getStartedAt(),
            order.getFinishedAt(),
            order.getDeliveredAt(),
            order.getUpdatedAt()
        );
    }
}
