package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer;

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
    boolean active,
    OffsetDateTime updatedAt
) {
}
