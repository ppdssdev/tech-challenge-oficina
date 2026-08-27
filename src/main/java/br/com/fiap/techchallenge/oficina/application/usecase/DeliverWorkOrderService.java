package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.DeliverWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.UUID;

public final class DeliverWorkOrderService implements DeliverWorkOrderUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public DeliverWorkOrderService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public WorkOrder deliver(UUID id) {
        return transactions.required(() -> {
            var order = workOrders.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            order.deliver();
            return workOrders.save(order);
        });
    }
}
