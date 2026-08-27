package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.StartDiagnosisUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class StartDiagnosisService implements StartDiagnosisUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public StartDiagnosisService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public WorkOrderResult start(UUID id, String notes) {
        return transactions.required(() -> {
            var order = find(id);
            order.startDiagnosis(notes);
            return toResult(workOrders.save(order));
        });
    }

    private WorkOrder find(UUID id) {
        return workOrders.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
    }
}
