package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import java.util.UUID;
import br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerWorkOrderResult;

public interface GetWorkOrderUseCase {
    WorkOrderResult get(UUID id);
    CustomerWorkOrderResult getForCustomer(UUID id, UUID authenticatedCustomerId);
    PublicWorkOrderStatusResult getPublicStatus(String code, String documentNumber);
}
