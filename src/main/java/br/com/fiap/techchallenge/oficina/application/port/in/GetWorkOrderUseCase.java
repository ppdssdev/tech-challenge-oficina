package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import java.util.UUID;

public interface GetWorkOrderUseCase {
    WorkOrderResult get(UUID id);
    PublicWorkOrderStatusResult getPublicStatus(String code, String documentNumber);
}
