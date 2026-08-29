package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.VehicleJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataVehicleRepository extends JpaRepository<VehicleJpaEntity, UUID> {
    @EntityGraph(attributePaths = "customer")
    Optional<VehicleJpaEntity> findByPlate(String plate);
    boolean existsByPlate(String plate);
    @Override
    @EntityGraph(attributePaths = "customer")
    Optional<VehicleJpaEntity> findById(UUID id);
}
