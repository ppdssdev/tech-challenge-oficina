package br.com.fiap.techchallenge.oficina.application.port.in.result;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkOrderResult(
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
    List<ServiceItem> services,
    List<PartItem> parts,
    OffsetDateTime customerAuthorizedAt,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime deliveredAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public WorkOrderResult {
        services = List.copyOf(services);
        parts = List.copyOf(parts);
    }

    public record ServiceItem(
        UUID serviceId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        int estimatedMinutes,
        BigDecimal lineTotal
    ) {
    }

    public record PartItem(
        UUID partId,
        String name,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean stockReserved
    ) {
    }
}
