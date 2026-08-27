package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

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
}
