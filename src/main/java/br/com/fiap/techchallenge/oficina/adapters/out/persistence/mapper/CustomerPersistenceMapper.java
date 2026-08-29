package br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.CustomerJpaEntity;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;

public final class CustomerPersistenceMapper {
    private CustomerPersistenceMapper() { }

    public static Customer toDomain(CustomerJpaEntity entity) {
        return Customer.restore(
            entity.getId(), entity.getFullName(), entity.getDocumentType(), entity.getDocumentNumber(),
            entity.getEmail(), entity.getPhone(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public static void updateEntity(Customer domain, CustomerJpaEntity entity) {
        if (domain.getId() != null) entity.setId(domain.getId());
        entity.setFullName(domain.getFullName());
        entity.setDocumentType(domain.getDocumentType());
        entity.setDocumentNumber(domain.getDocumentNumber());
        entity.setEmail(domain.getEmail());
        entity.setPhone(domain.getPhone());
    }
}
