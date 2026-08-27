package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.ServiceCatalogResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManageServiceCatalogUseCase {
    ServiceCatalogResult create(Command command);
    List<ServiceCatalogResult> list(boolean activeOnly);
    ServiceCatalogResult get(UUID id);
    ServiceCatalogResult update(UUID id, Command command);
    void deactivate(UUID id);

    record Command(
        String name, String description, BigDecimal basePrice, int estimatedMinutes, boolean active
    ) {
    }
}
