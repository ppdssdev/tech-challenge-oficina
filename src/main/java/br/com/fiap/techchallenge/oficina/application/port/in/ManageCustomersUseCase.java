package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import java.util.List;
import java.util.UUID;

public interface ManageCustomersUseCase {
    Customer create(Command command);
    List<Customer> list();
    Customer get(UUID id);
    Customer update(UUID id, Command command);
    void delete(UUID id);

    record Command(String fullName, DocumentType documentType, String documentNumber, String email, String phone) {
    }
}
