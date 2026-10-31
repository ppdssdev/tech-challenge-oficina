package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderStatusTransition(UUID id, WorkOrderStatus previousStatus,
                                        WorkOrderStatus newStatus, OffsetDateTime occurredAt) { }
