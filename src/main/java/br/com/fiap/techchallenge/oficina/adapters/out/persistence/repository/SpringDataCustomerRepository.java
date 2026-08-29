package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.CustomerJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {
    Optional<CustomerJpaEntity> findByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumber(String documentNumber);
}
