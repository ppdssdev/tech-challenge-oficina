package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManagePartsUseCase {
    Part create(Command command);
    List<Part> list(boolean activeOnly);
    List<Part> belowMinimumStock();
    Part get(UUID id);
    Part update(UUID id, Command command);
    Part increaseStock(UUID id, int quantity);
    Part decreaseStock(UUID id, int quantity);
    void deactivate(UUID id);

    record Command(
        String name, String sku, BigDecimal unitPrice, int quantityInStock, int minimumStock, boolean active
    ) {
    }
}
