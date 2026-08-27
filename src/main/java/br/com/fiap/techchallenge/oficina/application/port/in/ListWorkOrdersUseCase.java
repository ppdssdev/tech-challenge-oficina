package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderSummaryResult;
import java.util.List;

public interface ListWorkOrdersUseCase {
    List<WorkOrderSummaryResult> list(StatusFilter status);

    enum StatusFilter {
        RECEIVED,
        IN_DIAGNOSIS,
        WAITING_APPROVAL,
        IN_EXECUTION,
        FINALIZED,
        DELIVERED
    }
}
