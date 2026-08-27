package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.UUID;

public interface GetWorkOrderUseCase {
    WorkOrder get(UUID id);
    WorkOrder getPublicStatus(String code, String documentNumber);
}
