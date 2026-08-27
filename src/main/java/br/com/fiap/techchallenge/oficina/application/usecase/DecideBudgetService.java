package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.DecideBudgetUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class DecideBudgetService implements DecideBudgetUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final PartRepositoryPort parts;
    private final TransactionPort transactions;

    public DecideBudgetService(
        WorkOrderRepositoryPort workOrders,
        PartRepositoryPort parts,
        TransactionPort transactions
    ) {
        this.workOrders = workOrders;
        this.parts = parts;
        this.transactions = transactions;
    }

    @Override
    public WorkOrderResult approve(UUID id) {
        return transactions.required(() -> {
            var order = workOrders.findDetailedByIdForStockUpdate(id)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            order.approveBudget();

            var uniqueParts = new LinkedHashMap<UUID, br.com.fiap.techchallenge.oficina.domain.model.catalog.Part>();
            order.getPartItems().forEach(item -> uniqueParts.put(item.getPart().getId(), item.getPart()));
            uniqueParts.values().forEach(parts::save);
            return toResult(workOrders.save(order));
        });
    }
}
