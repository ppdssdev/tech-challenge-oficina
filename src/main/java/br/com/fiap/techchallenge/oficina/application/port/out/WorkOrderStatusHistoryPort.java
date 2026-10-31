package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatusTransition;
import java.util.List;
import java.util.UUID;

public interface WorkOrderStatusHistoryPort {
    List<WorkOrderStatusTransition> findByWorkOrderId(UUID workOrderId);
}
