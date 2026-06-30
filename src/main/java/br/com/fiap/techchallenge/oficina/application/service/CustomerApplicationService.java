package br.com.fiap.techchallenge.oficina.application.service;

import br.com.fiap.techchallenge.oficina.api.dto.customer.CustomerRequest;
import br.com.fiap.techchallenge.oficina.api.dto.customer.CustomerResponse;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerApplicationService {

    private final CustomerRepository repository;

    public CustomerApplicationService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String document = DocumentValidator.onlyDigits(request.documentNumber());
        if (repository.existsByDocumentNumber(document)) {
            throw new ConflictException("Já existe cliente cadastrado com esse CPF/CNPJ.");
        }
        var customer = new Customer(request.fullName(), request.documentType(), document, request.email(), request.phone());
        return CustomerResponse.from(repository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return repository.findAll().stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse detail(UUID id) {
        return CustomerResponse.from(findById(id));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        var customer = findById(id);
        String document = DocumentValidator.onlyDigits(request.documentNumber());
        repository.findByDocumentNumber(document)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new ConflictException("Já existe outro cliente cadastrado com esse CPF/CNPJ.");
            });
        customer.update(request.fullName(), request.documentType(), document, request.email(), request.phone());
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(UUID id) {
        var customer = findById(id);
        repository.delete(customer);
    }

    public Customer findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
    }
}
