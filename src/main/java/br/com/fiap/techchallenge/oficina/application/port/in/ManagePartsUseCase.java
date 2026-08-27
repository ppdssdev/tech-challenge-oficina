package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.PartResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManagePartsUseCase {
    PartResult create(Command command);
    List<PartResult> list(boolean activeOnly);
    List<PartResult> belowMinimumStock();
    PartResult get(UUID id);
    PartResult update(UUID id, Command command);
    PartResult increaseStock(UUID id, int quantity);
    PartResult decreaseStock(UUID id, int quantity);
    void deactivate(UUID id);

    record Command(
        String name, String sku, BigDecimal unitPrice, int quantityInStock, int minimumStock, boolean active
    ) {
    }
}
