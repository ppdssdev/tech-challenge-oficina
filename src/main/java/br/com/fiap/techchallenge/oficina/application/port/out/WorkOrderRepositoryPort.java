package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepositoryPort {
    WorkOrder save(WorkOrder order);
    boolean existsByCode(String code);
    Optional<WorkOrder> findByCode(String code);
    Optional<WorkOrder> findDetailedById(UUID id);
    Optional<WorkOrder> findDetailedByIdForStockUpdate(UUID id);
    List<WorkOrder> findAll();
    List<WorkOrder> findByStatus(WorkOrderStatus status);
    List<WorkOrder> findCompletedForMetrics();
    List<WorkOrder> findCompletedForMetricsFrom(OffsetDateTime from);
    List<WorkOrder> findCompletedForMetricsTo(OffsetDateTime to);
    List<WorkOrder> findCompletedForMetricsBetween(OffsetDateTime from, OffsetDateTime to);
}
