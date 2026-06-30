package br.com.fiap.techchallenge.oficina.domain.model.customer;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(
            name = "type",
            column = @Column(name = "document_type", nullable = false, length = 4)
        ),
        @AttributeOverride(
            name = "value",
            column = @Column(name = "document_number", nullable = false, unique = true, length = 14)
        )
    })
    private DocumentNumber documentNumber;

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
