package br.com.fiap.techchallenge.oficina.domain.model.workorder;

public enum WorkOrderStatus {
    RECEIVED("Recebida"),
    IN_DIAGNOSIS("Em diagnóstico"),
    WAITING_APPROVAL("Aguardando aprovação"),
    IN_EXECUTION("Em execução"),
    BUDGET_REJECTED("Orçamento recusado"),
    FINALIZED("Finalizada"),
    DELIVERED("Entregue");

    private final String label;

    WorkOrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
