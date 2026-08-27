package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.List;
import java.util.UUID;

public interface AddWorkOrderItemsUseCase {
    WorkOrder add(UUID id, Command command);

    interface Command {
        List<? extends ServiceItem> services();
        List<? extends PartItem> parts();
    }

    interface ServiceItem {
        UUID serviceId();
        int quantity();
    }

    interface PartItem {
        UUID partId();
        int quantity();
    }

    record DefaultCommand(List<ServiceItem> services, List<PartItem> parts) implements Command {
        public DefaultCommand {
            services = services == null ? List.of() : List.copyOf(services);
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }
}
