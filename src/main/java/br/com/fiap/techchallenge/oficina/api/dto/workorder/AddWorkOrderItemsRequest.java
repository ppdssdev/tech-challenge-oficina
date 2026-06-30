package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import jakarta.validation.Valid;
import java.util.List;

public record AddWorkOrderItemsRequest(
    @Valid List<RequestedServiceInput> services,
    @Valid List<RequestedPartInput> parts
) {
}
