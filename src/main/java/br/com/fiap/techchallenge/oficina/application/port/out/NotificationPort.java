package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;

/** Ponto de extensão para notificações, sem introduzir infraestrutura nesta entrega. */
public interface NotificationPort {
    void budgetAwaitingApproval(WorkOrder order);
}
