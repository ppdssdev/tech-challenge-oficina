package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatusTransition;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "work_order_status_history")
public class WorkOrderStatusHistoryJpaEntity {
    @Id private UUID id;
    @Column(name = "work_order_id", nullable = false) private UUID workOrderId;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 30) private WorkOrderStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 30) private WorkOrderStatus newStatus;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "event_order", nullable = false) private int eventOrder;

    protected WorkOrderStatusHistoryJpaEntity() { }

    public WorkOrderStatusHistoryJpaEntity(UUID orderId, WorkOrderStatusTransition event, int eventOrder) {
        id = event.id(); workOrderId = orderId; previousStatus = event.previousStatus();
        newStatus = event.newStatus(); occurredAt = event.occurredAt(); this.eventOrder = eventOrder;
    }

    public WorkOrderStatusTransition toDomain() {
        return new WorkOrderStatusTransition(id, previousStatus, newStatus, occurredAt);
    }
}
