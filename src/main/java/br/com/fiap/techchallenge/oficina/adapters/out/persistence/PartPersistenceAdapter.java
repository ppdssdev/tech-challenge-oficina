package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.PartJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper.PartPersistenceMapper;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataPartRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PartPersistenceAdapter implements PartRepositoryPort {
    private final SpringDataPartRepository repository;

    public PartPersistenceAdapter(SpringDataPartRepository repository) { this.repository = repository; }

    @Override
    public Part save(Part part) {
        var entity = part.getId() == null
            ? new PartJpaEntity()
            : repository.findById(part.getId()).orElseGet(PartJpaEntity::new);
        PartPersistenceMapper.updateEntity(part, entity);
        return PartPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override public Optional<Part> findById(UUID id) { return repository.findById(id).map(PartPersistenceMapper::toDomain); }
    @Override public Optional<Part> findByIdForStockUpdate(UUID id) { return repository.findByIdForStockUpdate(id).map(PartPersistenceMapper::toDomain); }
    @Override public Optional<Part> findBySku(String sku) { return repository.findBySku(sku).map(PartPersistenceMapper::toDomain); }
    @Override public boolean existsBySku(String sku) { return repository.existsBySku(sku); }
    @Override public List<Part> findAll() { return repository.findAll().stream().map(PartPersistenceMapper::toDomain).toList(); }
    @Override public List<Part> findActive() { return repository.findByActiveTrueOrderByNameAsc().stream().map(PartPersistenceMapper::toDomain).toList(); }
}
