package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.PartJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPartRepository extends JpaRepository<PartJpaEntity, UUID> {
    Optional<PartJpaEntity> findBySku(String sku);
    boolean existsBySku(String sku);
    List<PartJpaEntity> findByActiveTrueOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartJpaEntity p where p.id = :id")
    Optional<PartJpaEntity> findByIdForStockUpdate(@Param("id") UUID id);
}
