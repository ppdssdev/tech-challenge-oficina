package br.com.fiap.techchallenge.oficina.infrastructure.repository;

import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalogItem, UUID> {
    List<ServiceCatalogItem> findByActiveTrueOrderByNameAsc();
}
