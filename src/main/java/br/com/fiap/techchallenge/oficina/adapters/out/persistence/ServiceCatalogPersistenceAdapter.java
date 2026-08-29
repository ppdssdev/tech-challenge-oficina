package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.ServiceCatalogJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper.ServiceCatalogPersistenceMapper;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataServiceCatalogRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ServiceCatalogPersistenceAdapter implements ServiceCatalogRepositoryPort {
    private final SpringDataServiceCatalogRepository repository;

    public ServiceCatalogPersistenceAdapter(SpringDataServiceCatalogRepository repository) { this.repository = repository; }

    @Override
    public ServiceCatalogItem save(ServiceCatalogItem item) {
        var entity = item.getId() == null
            ? new ServiceCatalogJpaEntity()
            : repository.findById(item.getId()).orElseGet(ServiceCatalogJpaEntity::new);
        ServiceCatalogPersistenceMapper.updateEntity(item, entity);
        return ServiceCatalogPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override public Optional<ServiceCatalogItem> findById(UUID id) { return repository.findById(id).map(ServiceCatalogPersistenceMapper::toDomain); }
    @Override public List<ServiceCatalogItem> findAll() { return repository.findAll().stream().map(ServiceCatalogPersistenceMapper::toDomain).toList(); }
    @Override public List<ServiceCatalogItem> findActive() { return repository.findByActiveTrueOrderByNameAsc().stream().map(ServiceCatalogPersistenceMapper::toDomain).toList(); }
}
