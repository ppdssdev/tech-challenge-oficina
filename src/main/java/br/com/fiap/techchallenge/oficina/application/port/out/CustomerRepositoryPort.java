package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepositoryPort {
    Customer save(Customer customer);
    Optional<Customer> findById(UUID id);
    Optional<Customer> findByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumber(String documentNumber);
    List<Customer> findAll();
    void delete(Customer customer);
}
