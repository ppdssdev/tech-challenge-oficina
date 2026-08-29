package br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.CustomerJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.PartJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.ServiceCatalogJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.VehicleJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderPartItemJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderServiceItemJpaEntity;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class WorkOrderPersistenceMapper {
    private WorkOrderPersistenceMapper() { }

    public static WorkOrder toDomain(WorkOrderJpaEntity entity, boolean includeItems) {
        var services = includeItems
            ? entity.getServiceItems().stream().map(item -> new WorkOrder.ServiceItemSnapshot(
                item.getId(), ServiceCatalogPersistenceMapper.toDomain(item.getService()), item.getServiceName(),
                item.getUnitPrice(), item.getQuantity(), item.getEstimatedMinutes(), item.getLineTotal(),
                item.getCreatedAt(), item.getUpdatedAt()
            )).toList()
            : List.<WorkOrder.ServiceItemSnapshot>of();
        var parts = includeItems
            ? entity.getPartItems().stream().map(item -> new WorkOrder.PartItemSnapshot(
                item.getId(), PartPersistenceMapper.toDomain(item.getPart()), item.getPartName(), item.getSku(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal(), item.isStockReserved(),
                item.getCreatedAt(), item.getUpdatedAt()
            )).toList()
            : List.<WorkOrder.PartItemSnapshot>of();

        return WorkOrder.restore(
            entity.getId(), entity.getCode(), CustomerPersistenceMapper.toDomain(entity.getCustomer()),
            VehiclePersistenceMapper.toDomain(entity.getVehicle()), entity.getStatus(), entity.getDiagnosticNotes(),
            entity.getCustomerAuthorizedAt(), entity.getStartedAt(), entity.getFinishedAt(), entity.getDeliveredAt(),
            entity.getTotalServices(), entity.getTotalParts(), entity.getTotalAmount(), services, parts,
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public static void updateEntity(
        WorkOrder domain,
        WorkOrderJpaEntity entity,
        CustomerJpaEntity customer,
        VehicleJpaEntity vehicle,
        Function<UUID, ServiceCatalogJpaEntity> serviceReference,
        Function<UUID, PartJpaEntity> partReference
    ) {
        if (domain.getId() != null) entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setCustomer(customer);
        entity.setVehicle(vehicle);
        entity.setStatus(domain.getStatus());
        entity.setDiagnosticNotes(domain.getDiagnosticNotes());
        entity.setCustomerAuthorizedAt(domain.getCustomerAuthorizedAt());
        entity.setStartedAt(domain.getStartedAt());
        entity.setFinishedAt(domain.getFinishedAt());
        entity.setDeliveredAt(domain.getDeliveredAt());
        entity.setTotalServices(domain.getTotalServices());
        entity.setTotalParts(domain.getTotalParts());
        entity.setTotalAmount(domain.getTotalAmount());
        syncServices(domain, entity, serviceReference);
        syncParts(domain, entity, partReference);
    }

    private static void syncServices(
        WorkOrder domain,
        WorkOrderJpaEntity entity,
        Function<UUID, ServiceCatalogJpaEntity> references
    ) {
        Map<UUID, WorkOrderServiceItemJpaEntity> existing = new HashMap<>();
        entity.getServiceItems().stream()
            .filter(item -> item.getId() != null)
            .forEach(item -> existing.put(item.getId(), item));
        List<WorkOrderServiceItemJpaEntity> desired = new ArrayList<>();
        domain.getServiceItems().forEach(item -> {
            var target = item.getId() == null ? new WorkOrderServiceItemJpaEntity() : existing.get(item.getId());
            if (target == null) target = new WorkOrderServiceItemJpaEntity();
            if (item.getId() != null) target.setId(item.getId());
            target.setWorkOrder(entity);
            target.setService(references.apply(item.getService().getId()));
            target.setServiceName(item.getServiceName());
            target.setUnitPrice(item.getUnitPrice());
            target.setQuantity(item.getQuantity());
            target.setEstimatedMinutes(item.getEstimatedMinutes());
            target.setLineTotal(item.getLineTotal());
            desired.add(target);
        });
        entity.getServiceItems().clear();
        entity.getServiceItems().addAll(desired);
    }

    private static void syncParts(
        WorkOrder domain,
        WorkOrderJpaEntity entity,
        Function<UUID, PartJpaEntity> references
    ) {
        Map<UUID, WorkOrderPartItemJpaEntity> existing = new HashMap<>();
        entity.getPartItems().stream()
            .filter(item -> item.getId() != null)
            .forEach(item -> existing.put(item.getId(), item));
        List<WorkOrderPartItemJpaEntity> desired = new ArrayList<>();
        domain.getPartItems().forEach(item -> {
            var target = item.getId() == null ? new WorkOrderPartItemJpaEntity() : existing.get(item.getId());
            if (target == null) target = new WorkOrderPartItemJpaEntity();
            if (item.getId() != null) target.setId(item.getId());
            target.setWorkOrder(entity);
            target.setPart(references.apply(item.getPart().getId()));
            target.setPartName(item.getPartName());
            target.setSku(item.getSku());
            target.setUnitPrice(item.getUnitPrice());
            target.setQuantity(item.getQuantity());
            target.setLineTotal(item.getLineTotal());
            target.setStockReserved(item.isStockReserved());
            desired.add(target);
        });
        entity.getPartItems().clear();
        entity.getPartItems().addAll(desired);
    }
}
