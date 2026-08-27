package br.com.fiap.techchallenge.oficina.application.port.in.result;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PublicWorkOrderStatusResult(
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
}
