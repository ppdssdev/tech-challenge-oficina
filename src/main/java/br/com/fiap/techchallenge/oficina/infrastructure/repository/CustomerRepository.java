package br.com.fiap.techchallenge.oficina.infrastructure.repository;

import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumber(String documentNumber);
}
