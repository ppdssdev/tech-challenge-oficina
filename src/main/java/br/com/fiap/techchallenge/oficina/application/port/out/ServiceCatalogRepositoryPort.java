package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceCatalogRepositoryPort {
    ServiceCatalogItem save(ServiceCatalogItem item);
    Optional<ServiceCatalogItem> findById(UUID id);
    List<ServiceCatalogItem> findAll();
    List<ServiceCatalogItem> findActive();
}
