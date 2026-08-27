package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer;

import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
    @NotBlank @Size(max = 120) String fullName,
    @NotNull DocumentType documentType,
    @NotBlank @Size(max = 18) String documentNumber,
    @Email @Size(max = 160) String email,
    @Size(max = 20) String phone
) {
}
