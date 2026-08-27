package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import java.util.List;
import java.util.UUID;

public interface ManageVehiclesUseCase {
    Vehicle create(Command command);
    List<Vehicle> list();
    Vehicle get(UUID id);
    Vehicle update(UUID id, Command command);
    void delete(UUID id);

    record Command(UUID customerId, String plate, String brand, String model, int manufacturingYear) {
    }
}
