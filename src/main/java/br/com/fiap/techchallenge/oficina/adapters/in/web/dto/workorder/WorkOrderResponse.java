package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkOrderResponse(
    UUID id,
    String code,
    WorkOrderStatus status,
    String statusLabel,
    UUID customerId,
    String customerName,
    String customerDocumentNumber,
    UUID vehicleId,
    String vehiclePlate,
    String vehicleDescription,
    String diagnosticNotes,
    BigDecimal totalServices,
    BigDecimal totalParts,
    BigDecimal totalAmount,
    List<WorkOrderServiceItemResponse> services,
    List<WorkOrderPartItemResponse> parts,
    OffsetDateTime customerAuthorizedAt,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime deliveredAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static WorkOrderResponse from(WorkOrder order) {
        return new WorkOrderResponse(
            order.getId(),
            order.getCode(),
            order.getStatus(),
            order.getStatus().getLabel(),
            order.getCustomer().getId(),
            order.getCustomer().getFullName(),
            order.getCustomer().getDocumentNumber(),
            order.getVehicle().getId(),
            order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel() + " " + order.getVehicle().getManufacturingYear(),
            order.getDiagnosticNotes(),
            order.getTotalServices(),
            order.getTotalParts(),
            order.getTotalAmount(),
            order.getServiceItems().stream().map(WorkOrderServiceItemResponse::from).toList(),
            order.getPartItems().stream().map(WorkOrderPartItemResponse::from).toList(),
            order.getCustomerAuthorizedAt(),
            order.getStartedAt(),
            order.getFinishedAt(),
            order.getDeliveredAt(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
