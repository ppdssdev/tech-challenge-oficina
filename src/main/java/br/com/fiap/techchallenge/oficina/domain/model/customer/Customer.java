package br.com.fiap.techchallenge.oficina.domain.model.customer;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Customer extends BaseEntity {

    private String fullName;
    private DocumentNumber documentNumber;
    private String email;
    private String phone;

    public Customer(String fullName, DocumentType documentType, String documentNumber, String email, String phone) {
        update(fullName, documentType, documentNumber, email, phone);
    }

    public static Customer restore(
        UUID id, String fullName, DocumentType documentType, String documentNumber, String email, String phone,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var customer = new Customer(fullName, documentType, documentNumber, email, phone);
        customer.restoreMetadata(id, createdAt, updatedAt);
        return customer;
    }

    public void update(String fullName, DocumentType documentType, String documentNumber, String email, String phone) {
        if (isBlank(fullName)) {
            throw new BusinessException("Nome do cliente é obrigatório.");
        }
        this.fullName = fullName.trim();
        this.documentNumber = new DocumentNumber(documentType, documentNumber);
        this.email = normalize(email);
        this.phone = DocumentValidator.onlyDigits(phone);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getFullName() {
        return fullName;
    }

    public DocumentType getDocumentType() {
        return documentNumber.getType();
    }

    public String getDocumentNumber() {
        return documentNumber.getValue();
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}
