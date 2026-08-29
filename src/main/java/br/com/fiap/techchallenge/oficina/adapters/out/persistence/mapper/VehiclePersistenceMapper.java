package br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.CustomerJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.VehicleJpaEntity;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;

public final class VehiclePersistenceMapper {
    private VehiclePersistenceMapper() { }

    public static Vehicle toDomain(VehicleJpaEntity entity) {
        return Vehicle.restore(
            entity.getId(), CustomerPersistenceMapper.toDomain(entity.getCustomer()), entity.getPlate(),
            entity.getBrand(), entity.getModel(), entity.getManufacturingYear(),
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public static void updateEntity(Vehicle domain, VehicleJpaEntity entity, CustomerJpaEntity customer) {
        if (domain.getId() != null) entity.setId(domain.getId());
        entity.setCustomer(customer);
        entity.setPlate(domain.getPlate());
        entity.setBrand(domain.getBrand());
        entity.setModel(domain.getModel());
        entity.setManufacturingYear(domain.getManufacturingYear());
    }
}
