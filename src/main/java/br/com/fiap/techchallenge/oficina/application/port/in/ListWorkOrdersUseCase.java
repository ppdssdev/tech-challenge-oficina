package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.List;

public interface ListWorkOrdersUseCase {
    List<WorkOrder> list(StatusFilter status);

    enum StatusFilter {
        RECEIVED,
        IN_DIAGNOSIS,
        WAITING_APPROVAL,
        IN_EXECUTION,
        FINALIZED,
        DELIVERED
    }
}
