package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

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
}
