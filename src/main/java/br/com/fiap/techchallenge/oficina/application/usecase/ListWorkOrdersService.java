package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ListWorkOrdersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderSummaryResult;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ListWorkOrdersService implements ListWorkOrdersUseCase {

    private static final Set<WorkOrderStatus> OPERATIONAL_STATUSES = EnumSet.of(
        WorkOrderStatus.RECEIVED,
        WorkOrderStatus.IN_DIAGNOSIS,
        WorkOrderStatus.WAITING_APPROVAL,
        WorkOrderStatus.IN_EXECUTION
    );
    private static final Map<WorkOrderStatus, Integer> OPERATIONAL_PRIORITY = operationalPriority();
    private static final Comparator<WorkOrder> OPERATIONAL_ORDER = Comparator
        .comparingInt((WorkOrder order) -> OPERATIONAL_PRIORITY.get(order.getStatus()))
        .thenComparing(ListWorkOrdersService::operationalDate, Comparator.nullsLast(Comparator.naturalOrder()));

    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public ListWorkOrdersService(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    @Override
    public List<WorkOrderSummaryResult> list(StatusFilter status) {
        return transactions.required(() -> status == null
            ? listOperationalWorkOrders()
            : workOrders.findByStatus(WorkOrderStatus.valueOf(status.name())).stream()
                .map(ApplicationResultMapper::toSummaryResult)
                .toList());
    }

    private List<WorkOrderSummaryResult> listOperationalWorkOrders() {
        return workOrders.findAll().stream()
            .filter(order -> OPERATIONAL_STATUSES.contains(order.getStatus()))
            .sorted(OPERATIONAL_ORDER)
            .map(ApplicationResultMapper::toSummaryResult)
            .toList();
    }

    private static Map<WorkOrderStatus, Integer> operationalPriority() {
        var priorities = new EnumMap<WorkOrderStatus, Integer>(WorkOrderStatus.class);
        priorities.put(WorkOrderStatus.IN_EXECUTION, 1);
        priorities.put(WorkOrderStatus.WAITING_APPROVAL, 2);
        priorities.put(WorkOrderStatus.IN_DIAGNOSIS, 3);
        priorities.put(WorkOrderStatus.RECEIVED, 4);
        return Map.copyOf(priorities);
    }

    private static OffsetDateTime operationalDate(WorkOrder order) {
        return order.getCreatedAt() != null ? order.getCreatedAt() : order.getUpdatedAt();
    }
}
