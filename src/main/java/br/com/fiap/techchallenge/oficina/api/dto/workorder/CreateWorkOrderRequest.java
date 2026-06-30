package br.com.fiap.techchallenge.oficina.api.dto.workorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateWorkOrderRequest(
    @Valid @NotNull WorkOrderCustomerInput customer,
    @Valid @NotNull WorkOrderVehicleInput vehicle,
    @Valid @NotEmpty(message = "Informe pelo menos um serviço solicitado.") List<RequestedServiceInput> services,
    @Valid List<RequestedPartInput> parts,
    @Size(max = 2000) String diagnosticNotes
) {
}
