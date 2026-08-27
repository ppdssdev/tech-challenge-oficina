package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ManageServiceCatalogUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.ServiceCatalogResult;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class ManageServiceCatalogService implements ManageServiceCatalogUseCase {
    private final ServiceCatalogRepositoryPort services;
    private final TransactionPort transactions;

    public ManageServiceCatalogService(ServiceCatalogRepositoryPort services, TransactionPort transactions) {
        this.services = services;
        this.transactions = transactions;
    }

    @Override
    public ServiceCatalogResult create(Command command) {
        return transactions.required(() -> {
            var item = new ServiceCatalogItem(command.name(), command.description(), command.basePrice(), command.estimatedMinutes());
            item.update(command.name(), command.description(), command.basePrice(), command.estimatedMinutes(), command.active());
            return toResult(services.save(item));
        });
    }

    @Override
    public List<ServiceCatalogResult> list(boolean activeOnly) {
        return transactions.required(() -> (activeOnly ? services.findActive() : services.findAll())
            .stream().map(ApplicationResultMapper::toResult).toList());
    }

    @Override
    public ServiceCatalogResult get(UUID id) {
        return transactions.required(() -> toResult(find(id)));
    }

    @Override
    public ServiceCatalogResult update(UUID id, Command command) {
        return transactions.required(() -> {
            var item = find(id);
            item.update(command.name(), command.description(), command.basePrice(), command.estimatedMinutes(), command.active());
            return toResult(services.save(item));
        });
    }

    @Override
    public void deactivate(UUID id) {
        transactions.required(() -> {
            var item = find(id);
            item.deactivate();
            services.save(item);
            return null;
        });
    }

    private ServiceCatalogItem find(UUID id) {
        return services.findById(id).orElseThrow(() -> new NotFoundException("Serviço não encontrado."));
    }
}
