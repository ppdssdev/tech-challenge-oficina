package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderServiceItem;
import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderServiceItemResponse(
    UUID serviceId,
    String name,
    BigDecimal unitPrice,
    int quantity,
    int estimatedMinutes,
    BigDecimal lineTotal
) {
    public static WorkOrderServiceItemResponse from(WorkOrderServiceItem item) {
        return new WorkOrderServiceItemResponse(
            item.getService().getId(), item.getServiceName(), item.getUnitPrice(), item.getQuantity(),
            item.getEstimatedMinutes(), item.getLineTotal()
        );
    }
}
