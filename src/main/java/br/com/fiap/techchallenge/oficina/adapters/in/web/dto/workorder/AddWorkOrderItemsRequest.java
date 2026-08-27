package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder;

import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import jakarta.validation.Valid;
import java.util.List;

public record AddWorkOrderItemsRequest(
    @Valid List<RequestedServiceInput> services,
    @Valid List<RequestedPartInput> parts
) implements AddWorkOrderItemsUseCase.Command {
}
