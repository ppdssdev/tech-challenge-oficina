package br.com.fiap.techchallenge.oficina.api.dto.customer;

import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String fullName,
    DocumentType documentType,
    String documentNumber,
    String email,
    String phone,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getFullName(),
            customer.getDocumentType(),
            customer.getDocumentNumber(),
            customer.getEmail(),
            customer.getPhone(),
            customer.getCreatedAt(),
            customer.getUpdatedAt()
        );
    }
}
