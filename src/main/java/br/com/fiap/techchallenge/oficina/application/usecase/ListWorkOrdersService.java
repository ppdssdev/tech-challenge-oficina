package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ListWorkOrdersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.util.List;

public final class ListWorkOrdersService implements ListWorkOrdersUseCase {

    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public ListWorkOrdersService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public List<WorkOrder> list(StatusFilter status) {
        return transactions.required(() -> status == null
            ? workOrders.findAll()
            : workOrders.findByStatus(WorkOrderStatus.valueOf(status.name())));
    }
}
