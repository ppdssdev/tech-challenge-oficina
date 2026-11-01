package br.com.fiap.techchallenge.oficina.application.port.in.result;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.math.BigDecimal;
import java.util.UUID;
public record CustomerWorkOrderResult(UUID id, String code, WorkOrderStatus status, BigDecimal totalAmount) {}
