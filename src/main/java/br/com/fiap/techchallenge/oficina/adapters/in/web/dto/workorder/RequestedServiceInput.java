package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequestedServiceInput(
    @NotNull UUID serviceId,
    @Min(1) int quantity
) implements AddWorkOrderItemsUseCase.ServiceItem {
}
