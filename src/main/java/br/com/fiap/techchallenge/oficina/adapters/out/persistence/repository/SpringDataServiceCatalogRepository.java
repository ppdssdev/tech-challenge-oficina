package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.ServiceCatalogJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataServiceCatalogRepository extends JpaRepository<ServiceCatalogJpaEntity, UUID> {
    List<ServiceCatalogJpaEntity> findByActiveTrueOrderByNameAsc();
}
