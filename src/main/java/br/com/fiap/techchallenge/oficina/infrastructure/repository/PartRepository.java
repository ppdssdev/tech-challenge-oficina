package br.com.fiap.techchallenge.oficina.infrastructure.repository;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartRepository extends JpaRepository<Part, UUID> {
    Optional<Part> findBySku(String sku);
    boolean existsBySku(String sku);
    List<Part> findByActiveTrueOrderByNameAsc();
    List<Part> findByQuantityInStockLessThanEqual(int quantity);
}
