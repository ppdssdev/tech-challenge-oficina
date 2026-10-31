package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderStatusHistoryJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataWorkOrderStatusHistoryRepository extends JpaRepository<WorkOrderStatusHistoryJpaEntity, UUID> {
    List<WorkOrderStatusHistoryJpaEntity> findByWorkOrderIdOrderByEventOrderAsc(UUID workOrderId);
    long countByWorkOrderId(UUID workOrderId);
}
