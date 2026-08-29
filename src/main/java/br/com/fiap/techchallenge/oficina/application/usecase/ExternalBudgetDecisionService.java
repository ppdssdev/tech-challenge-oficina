package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.util.LinkedHashMap;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toPublicStatusResult;

public final class ExternalBudgetDecisionService implements ExternalBudgetDecisionUseCase {

    private static final String NOT_FOUND_MESSAGE =
        "Ordem de serviço não encontrada para o documento informado.";

    private final WorkOrderRepositoryPort workOrders;
    private final PartRepositoryPort parts;
    private final TransactionPort transactions;

    public ExternalBudgetDecisionService(
        WorkOrderRepositoryPort workOrders,
        PartRepositoryPort parts,
        TransactionPort transactions
    ) {
        this.workOrders = workOrders;
        this.parts = parts;
        this.transactions = transactions;
    }

    @Override
    public PublicWorkOrderStatusResult approve(Command command) {
        return transactions.required(() -> {
            var order = findAuthorizedOrderForUpdate(command);
            order.approveBudget();
            saveParts(order);
            return toPublicStatusResult(workOrders.save(order));
        });
    }

    @Override
    public PublicWorkOrderStatusResult reject(Command command) {
        return transactions.required(() -> {
            var order = findAuthorizedOrderForUpdate(command);
            order.rejectBudget();
            return toPublicStatusResult(workOrders.save(order));
        });
    }

    private WorkOrder findAuthorizedOrderForUpdate(Command command) {
        var orderReference = workOrders.findByCode(command.code())
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
        ensureDocumentMatches(orderReference, command.document());

        var order = workOrders.findDetailedByIdForStockUpdate(orderReference.getId())
            .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
        ensureDocumentMatches(order, command.document());
        return order;
    }

    private void ensureDocumentMatches(WorkOrder order, String informedDocument) {
        String document = DocumentValidator.onlyDigits(informedDocument);
        if (!order.getCustomer().getDocumentNumber().equals(document)) {
            throw new NotFoundException(NOT_FOUND_MESSAGE);
        }
    }

    private void saveParts(WorkOrder order) {
        var uniqueParts = new LinkedHashMap<UUID, Part>();
        order.getPartItems().forEach(item -> uniqueParts.put(item.getPart().getId(), item.getPart()));
        uniqueParts.values().forEach(parts::save);
    }
}
