package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.util.UUID;

public interface StartDiagnosisUseCase {
    WorkOrder start(UUID id, String notes);
}
