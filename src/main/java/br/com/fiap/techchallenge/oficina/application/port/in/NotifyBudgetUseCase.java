package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.BudgetNotificationResult;
import java.util.UUID;

public interface NotifyBudgetUseCase {
    BudgetNotificationResult notifyBudget(UUID workOrderId);
}
