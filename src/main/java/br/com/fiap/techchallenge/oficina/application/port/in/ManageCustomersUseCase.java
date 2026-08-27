package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerResult;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import java.util.List;
import java.util.UUID;

public interface ManageCustomersUseCase {
    CustomerResult create(Command command);
    List<CustomerResult> list();
    CustomerResult get(UUID id);
    CustomerResult update(UUID id, Command command);
    void delete(UUID id);

    record Command(String fullName, DocumentType documentType, String documentNumber, String email, String phone) {
    }
}
