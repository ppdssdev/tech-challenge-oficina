package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ManageServiceCatalogUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.util.List;
import java.util.UUID;

public final class ManageServiceCatalogService implements ManageServiceCatalogUseCase {
    private final ServiceCatalogRepositoryPort services;
    private final TransactionPort transactions;

    public ManageServiceCatalogService(ServiceCatalogRepositoryPort services, TransactionPort transactions) {
        this.services = services;
        this.transactions = transactions;
    }

    @Override
    public ServiceCatalogItem create(Command command) {
        return transactions.required(() -> {
            var item = new ServiceCatalogItem(command.name(), command.description(), command.basePrice(), command.estimatedMinutes());
            item.update(command.name(), command.description(), command.basePrice(), command.estimatedMinutes(), command.active());
            return services.save(item);
        });
    }

    @Override
    public List<ServiceCatalogItem> list(boolean activeOnly) {
        return transactions.required(() -> activeOnly ? services.findActive() : services.findAll());
    }

    @Override
    public ServiceCatalogItem get(UUID id) {
        return transactions.required(() -> find(id));
    }

    @Override
    public ServiceCatalogItem update(UUID id, Command command) {
        return transactions.required(() -> {
            var item = find(id);
            item.update(command.name(), command.description(), command.basePrice(), command.estimatedMinutes(), command.active());
            return services.save(item);
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
