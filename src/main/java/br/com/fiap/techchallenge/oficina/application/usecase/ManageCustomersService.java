package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerResult;
import br.com.fiap.techchallenge.oficina.application.port.out.CustomerRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class ManageCustomersService implements ManageCustomersUseCase {
    private final CustomerRepositoryPort customers;
    private final TransactionPort transactions;

    public ManageCustomersService(CustomerRepositoryPort customers, TransactionPort transactions) {
        this.customers = customers;
        this.transactions = transactions;
    }

    @Override
    public CustomerResult create(Command command) {
        return transactions.required(() -> {
            String document = DocumentValidator.onlyDigits(command.documentNumber());
            if (customers.existsByDocumentNumber(document)) {
                throw new ConflictException("Já existe cliente cadastrado com esse CPF/CNPJ.");
            }
            return toResult(customers.save(new Customer(
                command.fullName(), command.documentType(), document, command.email(), command.phone()
            )));
        });
    }

    @Override
    public List<CustomerResult> list() {
        return transactions.required(() -> customers.findAll().stream().map(ApplicationResultMapper::toResult).toList());
    }

    @Override
    public CustomerResult get(UUID id) {
        return transactions.required(() -> toResult(find(id)));
    }

    @Override
    public CustomerResult update(UUID id, Command command) {
        return transactions.required(() -> {
            var customer = find(id);
            String document = DocumentValidator.onlyDigits(command.documentNumber());
            customers.findByDocumentNumber(document)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Já existe outro cliente cadastrado com esse CPF/CNPJ.");
                });
            customer.update(command.fullName(), command.documentType(), document, command.email(), command.phone());
            return toResult(customers.save(customer));
        });
    }

    @Override
    public void delete(UUID id) {
        transactions.required(() -> {
            customers.delete(find(id));
            return null;
        });
    }

    private Customer find(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
    }
}
