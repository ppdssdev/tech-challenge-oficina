package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity(name = "CustomerJpaEntity")
@Table(name = "customers")
public class CustomerJpaEntity extends JpaBaseEntity {
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

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
