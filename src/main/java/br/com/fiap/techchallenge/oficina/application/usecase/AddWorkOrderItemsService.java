package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class AddWorkOrderItemsService implements AddWorkOrderItemsUseCase {
    private final WorkOrderRepositoryPort workOrders;
    private final ServiceCatalogRepositoryPort services;
    private final PartRepositoryPort parts;
    private final TransactionPort transactions;

    public AddWorkOrderItemsService(
        WorkOrderRepositoryPort workOrders,
        ServiceCatalogRepositoryPort services,
        PartRepositoryPort parts,
        TransactionPort transactions
    ) {
        this.workOrders = workOrders;
        this.services = services;
        this.parts = parts;
        this.transactions = transactions;
    }

    @Override
    public WorkOrderResult add(UUID id, Command command) {
        return transactions.required(() -> {
            var order = workOrders.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
            boolean hasServices = command.services() != null && !command.services().isEmpty();
            boolean hasParts = command.parts() != null && !command.parts().isEmpty();
            if (!hasServices && !hasParts) {
                throw new BusinessException("Informe ao menos um serviço ou peça para adicionar à OS.");
            }
            if (hasServices) {
                command.services().forEach(input -> order.addRequestedService(
                    services.findById(input.serviceId())
                        .orElseThrow(() -> new NotFoundException("Serviço informado na OS não encontrado.")),
                    input.quantity()
                ));
            }
            if (hasParts) {
                command.parts().forEach(input -> order.addRequiredPart(
                    parts.findById(input.partId())
                        .orElseThrow(() -> new NotFoundException("Peça/insumo informado na OS não encontrado.")),
                    input.quantity()
                ));
            }
            return toResult(workOrders.save(order));
        });
    }
}
