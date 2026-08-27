package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.UpdateDiagnosisUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class UpdateDiagnosisService implements UpdateDiagnosisUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public UpdateDiagnosisService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public WorkOrderResult update(UUID id, String notes) {
        return transactions.required(() -> {
            var order = workOrders.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            order.updateDiagnosticNotes(notes);
            return toResult(workOrders.save(order));
        });
    }
}
