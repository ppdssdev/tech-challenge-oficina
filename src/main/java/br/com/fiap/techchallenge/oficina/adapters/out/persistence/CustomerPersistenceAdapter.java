package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.CustomerJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper.CustomerPersistenceMapper;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataCustomerRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.CustomerRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CustomerPersistenceAdapter implements CustomerRepositoryPort {
    private final SpringDataCustomerRepository repository;

    public CustomerPersistenceAdapter(SpringDataCustomerRepository repository) { this.repository = repository; }

    @Override
    public Customer save(Customer customer) {
        var entity = customer.getId() == null
            ? new CustomerJpaEntity()
            : repository.findById(customer.getId()).orElseGet(CustomerJpaEntity::new);
        CustomerPersistenceMapper.updateEntity(customer, entity);
        return CustomerPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override public Optional<Customer> findById(UUID id) { return repository.findById(id).map(CustomerPersistenceMapper::toDomain); }
    @Override public Optional<Customer> findByDocumentNumber(String value) { return repository.findByDocumentNumber(value).map(CustomerPersistenceMapper::toDomain); }
    @Override public boolean existsByDocumentNumber(String value) { return repository.existsByDocumentNumber(value); }
    @Override public List<Customer> findAll() { return repository.findAll().stream().map(CustomerPersistenceMapper::toDomain).toList(); }
    @Override public void delete(Customer customer) { repository.deleteById(customer.getId()); }
}
