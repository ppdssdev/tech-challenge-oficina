package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderStatusHistoryJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataWorkOrderStatusHistoryRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderStatusHistoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatusTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkOrderStatusHistoryAdapter implements WorkOrderStatusHistoryPort {
    private final SpringDataWorkOrderStatusHistoryRepository repository;
    public WorkOrderStatusHistoryAdapter(SpringDataWorkOrderStatusHistoryRepository repository) { this.repository = repository; }
    @Override public List<WorkOrderStatusTransition> findByWorkOrderId(UUID workOrderId) {
        return repository.findByWorkOrderIdOrderByEventOrderAsc(workOrderId).stream()
            .map(WorkOrderStatusHistoryJpaEntity::toDomain).toList();
    }
}
