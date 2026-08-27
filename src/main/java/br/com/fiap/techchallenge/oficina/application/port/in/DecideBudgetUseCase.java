package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import java.util.UUID;

public interface DecideBudgetUseCase {
    WorkOrderResult approve(UUID id);
}
