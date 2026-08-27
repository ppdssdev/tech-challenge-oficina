package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.FinishWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class FinishWorkOrderService implements FinishWorkOrderUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public FinishWorkOrderService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public WorkOrderResult finish(UUID id) {
        return transactions.required(() -> {
            var order = workOrders.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            order.finish();
            return toResult(workOrders.save(order));
        });
    }
}
