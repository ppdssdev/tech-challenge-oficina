package br.com.fiap.techchallenge.oficina.application.port.in.result;

import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResult(
    UUID id,
    String fullName,
    DocumentType documentType,
    String documentNumber,
    String email,
    String phone,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
