package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import java.util.List;
import java.util.UUID;

public interface CreateWorkOrderUseCase {
    WorkOrderResult create(Command command);

    record Command(
        CustomerData customer,
        VehicleData vehicle,
        List<Item> services,
        List<Item> parts,
        String diagnosticNotes
    ) {
        public Command {
            services = services == null ? List.of() : List.copyOf(services);
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }

    record CustomerData(
        String fullName, DocumentType documentType, String documentNumber, String email, String phone
    ) {
    }

    record VehicleData(String plate, String brand, String model, int manufacturingYear) {
    }

    record Item(UUID id, int quantity) {
    }
}
