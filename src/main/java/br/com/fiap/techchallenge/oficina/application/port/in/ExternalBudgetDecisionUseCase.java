package br.com.fiap.techchallenge.oficina.application.port.in;

import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;

public interface ExternalBudgetDecisionUseCase {
    PublicWorkOrderStatusResult approve(Command command);
    PublicWorkOrderStatusResult reject(Command command);

    record Command(String code, String document) {
    }
}
