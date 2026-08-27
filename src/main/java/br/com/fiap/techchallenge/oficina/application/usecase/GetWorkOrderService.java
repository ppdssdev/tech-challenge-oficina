package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.util.UUID;

public final class GetWorkOrderService implements GetWorkOrderUseCase {

    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public GetWorkOrderService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public WorkOrder get(UUID id) {
        return transactions.required(() -> workOrders.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada.")));
    }

    @Override
    public WorkOrder getPublicStatus(String code, String documentNumber) {
        return transactions.required(() -> {
            var order = workOrders.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            String document = DocumentValidator.onlyDigits(documentNumber);
            if (!order.getCustomer().getDocumentNumber().equals(document)) {
                throw new NotFoundException("Ordem de serviço não encontrada para o documento informado.");
            }
            return order;
        });
    }
}
