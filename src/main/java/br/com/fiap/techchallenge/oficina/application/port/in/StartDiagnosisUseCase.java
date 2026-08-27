package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import java.util.UUID;

public interface StartDiagnosisUseCase {
    WorkOrderResult start(UUID id, String notes);
}
