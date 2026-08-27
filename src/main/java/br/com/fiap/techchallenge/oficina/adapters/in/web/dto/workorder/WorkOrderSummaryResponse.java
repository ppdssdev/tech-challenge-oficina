package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

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
}
