package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartRepositoryPort {
    Part save(Part part);
    Optional<Part> findById(UUID id);
    Optional<Part> findByIdForStockUpdate(UUID id);
    Optional<Part> findBySku(String sku);
    boolean existsBySku(String sku);
    List<Part> findAll();
    List<Part> findActive();
}
