package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepositoryPort {
    Vehicle save(Vehicle vehicle);
    Optional<Vehicle> findById(UUID id);
    Optional<Vehicle> findByPlate(String plate);
    boolean existsByPlate(String plate);
    List<Vehicle> findAll();
    void delete(Vehicle vehicle);
}
