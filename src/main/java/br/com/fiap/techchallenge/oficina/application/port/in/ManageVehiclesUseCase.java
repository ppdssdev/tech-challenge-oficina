package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.VehicleResult;
import java.util.List;
import java.util.UUID;

public interface ManageVehiclesUseCase {
    VehicleResult create(Command command);
    List<VehicleResult> list();
    VehicleResult get(UUID id);
    VehicleResult update(UUID id, Command command);
    void delete(UUID id);

    record Command(UUID customerId, String plate, String brand, String model, int manufacturingYear) {
    }
}
