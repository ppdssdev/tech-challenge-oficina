package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper.WorkOrderPersistenceMapper;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataCustomerRepository;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataPartRepository;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataServiceCatalogRepository;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataVehicleRepository;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataWorkOrderRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkOrderPersistenceAdapter implements WorkOrderRepositoryPort {
    private final SpringDataWorkOrderRepository repository;
    private final SpringDataCustomerRepository customers;
    private final SpringDataVehicleRepository vehicles;
    private final SpringDataServiceCatalogRepository services;
    private final SpringDataPartRepository parts;

    public WorkOrderPersistenceAdapter(
        SpringDataWorkOrderRepository repository,
        SpringDataCustomerRepository customers,
        SpringDataVehicleRepository vehicles,
        SpringDataServiceCatalogRepository services,
        SpringDataPartRepository parts
    ) {
        this.repository = repository;
        this.customers = customers;
        this.vehicles = vehicles;
        this.services = services;
        this.parts = parts;
    }

    @Override
    public WorkOrder save(WorkOrder order) {
        WorkOrderJpaEntity entity = order.getId() == null ? new WorkOrderJpaEntity() : loadDetailedEntity(order.getId());
        WorkOrderPersistenceMapper.updateEntity(
            order,
            entity,
            customers.getReferenceById(order.getCustomer().getId()),
            vehicles.getReferenceById(order.getVehicle().getId()),
            services::getReferenceById,
            parts::getReferenceById
        );
        return WorkOrderPersistenceMapper.toDomain(repository.saveAndFlush(entity), true);
    }

    @Override public boolean existsByCode(String code) { return repository.existsByCode(code); }
    @Override public Optional<WorkOrder> findByCode(String code) { return repository.findByCode(code).map(entity -> WorkOrderPersistenceMapper.toDomain(entity, false)); }
    @Override public Optional<WorkOrder> findDetailedById(UUID id) { return detailed(id).map(entity -> WorkOrderPersistenceMapper.toDomain(entity, true)); }

    @Override
    public Optional<WorkOrder> findDetailedByIdForStockUpdate(UUID id) {
        var lockedOrder = repository.findByIdForUpdate(id);
        if (lockedOrder.isEmpty()) return Optional.empty();
        repository.loadServiceItems(id);
        repository.loadPartItems(id);
        lockedOrder.get().getPartItems().stream()
            .map(item -> item.getPart().getId())
            .distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(parts::findByIdForStockUpdate);
        return Optional.of(WorkOrderPersistenceMapper.toDomain(lockedOrder.get(), true));
    }

    @Override public List<WorkOrder> findAll() { return repository.findAll().stream().map(entity -> WorkOrderPersistenceMapper.toDomain(entity, false)).toList(); }
    @Override public List<WorkOrder> findByStatus(WorkOrderStatus status) { return repository.findByStatus(status).stream().map(entity -> WorkOrderPersistenceMapper.toDomain(entity, false)).toList(); }
    @Override public List<WorkOrder> findCompletedForMetrics() { return summaries(repository.findCompletedForMetrics()); }
    @Override public List<WorkOrder> findCompletedForMetricsFrom(OffsetDateTime from) { return summaries(repository.findCompletedForMetricsFrom(from)); }
    @Override public List<WorkOrder> findCompletedForMetricsTo(OffsetDateTime to) { return summaries(repository.findCompletedForMetricsTo(to)); }
    @Override public List<WorkOrder> findCompletedForMetricsBetween(OffsetDateTime from, OffsetDateTime to) { return summaries(repository.findCompletedForMetricsBetween(from, to)); }

    private Optional<WorkOrderJpaEntity> detailed(UUID id) {
        var entity = repository.findDetailedById(id);
        if (entity.isPresent()) {
            repository.loadServiceItems(id);
            repository.loadPartItems(id);
        }
        return entity;
    }

    private WorkOrderJpaEntity loadDetailedEntity(UUID id) {
        return detailed(id).orElseThrow(() -> new IllegalStateException("OS removida durante a transação."));
    }

    private List<WorkOrder> summaries(List<WorkOrderJpaEntity> entities) {
        return entities.stream().map(entity -> WorkOrderPersistenceMapper.toDomain(entity, false)).toList();
    }
}
