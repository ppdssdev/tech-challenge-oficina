package br.com.fiap.techchallenge.oficina.application.port.in.result;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderSummaryResult(
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
}
