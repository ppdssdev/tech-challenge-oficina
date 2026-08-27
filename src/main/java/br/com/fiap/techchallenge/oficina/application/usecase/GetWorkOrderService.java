package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toPublicStatusResult;
import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class GetWorkOrderService implements GetWorkOrderUseCase {

    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public GetWorkOrderService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public WorkOrderResult get(UUID id) {
        return transactions.required(() -> toResult(workOrders.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."))));
    }

    @Override
    public PublicWorkOrderStatusResult getPublicStatus(String code, String documentNumber) {
        return transactions.required(() -> {
            var order = workOrders.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            String document = DocumentValidator.onlyDigits(documentNumber);
            if (!order.getCustomer().getDocumentNumber().equals(document)) {
                throw new NotFoundException("Ordem de serviço não encontrada para o documento informado.");
            }
            return toPublicStatusResult(order);
        });
    }
}
