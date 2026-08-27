package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import java.util.List;
import java.util.UUID;

public interface AddWorkOrderItemsUseCase {
    WorkOrderResult add(UUID id, Command command);

    record Command(List<ServiceItem> services, List<PartItem> parts) {
        public Command {
            services = services == null ? List.of() : List.copyOf(services);
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }

    record ServiceItem(UUID serviceId, int quantity) {
    }

    record PartItem(UUID partId, int quantity) {
    }
}
