package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderPartItem;
import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderPartItemResponse(
    UUID partId,
    String name,
    String sku,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal,
    boolean stockReserved
) {
    public static WorkOrderPartItemResponse from(WorkOrderPartItem item) {
        return new WorkOrderPartItemResponse(
            item.getPart().getId(), item.getPartName(), item.getSku(), item.getUnitPrice(),
            item.getQuantity(), item.getLineTotal(), item.isStockReserved()
        );
    }
}
