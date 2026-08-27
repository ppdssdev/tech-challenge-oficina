package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManageServiceCatalogUseCase {
    ServiceCatalogItem create(Command command);
    List<ServiceCatalogItem> list(boolean activeOnly);
    ServiceCatalogItem get(UUID id);
    ServiceCatalogItem update(UUID id, Command command);
    void deactivate(UUID id);

    record Command(
        String name, String description, BigDecimal basePrice, int estimatedMinutes, boolean active
    ) {
    }
}
