package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.VehicleJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper.VehiclePersistenceMapper;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataCustomerRepository;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataVehicleRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.VehicleRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VehiclePersistenceAdapter implements VehicleRepositoryPort {
    private final SpringDataVehicleRepository repository;
    private final SpringDataCustomerRepository customers;

    public VehiclePersistenceAdapter(SpringDataVehicleRepository repository, SpringDataCustomerRepository customers) {
        this.repository = repository;
        this.customers = customers;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        var entity = vehicle.getId() == null
            ? new VehicleJpaEntity()
            : repository.findById(vehicle.getId()).orElseGet(VehicleJpaEntity::new);
        VehiclePersistenceMapper.updateEntity(vehicle, entity, customers.getReferenceById(vehicle.getCustomer().getId()));
        return VehiclePersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override public Optional<Vehicle> findById(UUID id) { return repository.findById(id).map(VehiclePersistenceMapper::toDomain); }
    @Override public Optional<Vehicle> findByPlate(String plate) { return repository.findByPlate(plate).map(VehiclePersistenceMapper::toDomain); }
    @Override public boolean existsByPlate(String plate) { return repository.existsByPlate(plate); }
    @Override public List<Vehicle> findAll() { return repository.findAll().stream().map(VehiclePersistenceMapper::toDomain).toList(); }
    @Override public void delete(Vehicle vehicle) { repository.deleteById(vehicle.getId()); }
}
