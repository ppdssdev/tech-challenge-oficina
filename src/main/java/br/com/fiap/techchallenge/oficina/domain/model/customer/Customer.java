package br.com.fiap.techchallenge.oficina.domain.model.customer;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 4)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, unique = true, length = 14)
    private String documentNumber;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    protected Customer() {
    }

    public Customer(String fullName, DocumentType documentType, String documentNumber, String email, String phone) {
        update(fullName, documentType, documentNumber, email, phone);
    }

    public void update(String fullName, DocumentType documentType, String documentNumber, String email, String phone) {
        if (isBlank(fullName)) {
            throw new BusinessException("Nome do cliente é obrigatório.");
        }
        if (documentType == null) {
            throw new BusinessException("Tipo do documento é obrigatório.");
        }

        var normalizedDocument = DocumentValidator.onlyDigits(documentNumber);
        if (!DocumentValidator.isValid(documentType, normalizedDocument)) {
            throw new BusinessException("Documento inválido para o tipo informado.");
        }

        this.fullName = fullName.trim();
        this.documentType = documentType;
        this.documentNumber = normalizedDocument;
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
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}
