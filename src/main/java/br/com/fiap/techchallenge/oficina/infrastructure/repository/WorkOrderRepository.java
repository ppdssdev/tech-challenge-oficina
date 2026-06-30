package br.com.fiap.techchallenge.oficina.infrastructure.repository;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"customer"})
    Optional<WorkOrder> findByCode(String code);

    @EntityGraph(attributePaths = {"customer", "vehicle"})
    @Query("select w from WorkOrder w where w.id = :id")
    Optional<WorkOrder> findDetailedById(@Param("id") UUID id);

    @Query("""
        select distinct w
        from WorkOrder w
        left join fetch w.serviceItems serviceItem
        left join fetch serviceItem.service
        where w.id = :id
        """)
    Optional<WorkOrder> findDetailedServiceItemsById(@Param("id") UUID id);

    @Query("""
        select distinct w
        from WorkOrder w
        left join fetch w.partItems partItem
        left join fetch partItem.part
        where w.id = :id
        """)
    Optional<WorkOrder> findDetailedPartItemsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct w
        from WorkOrder w
        left join fetch w.partItems partItem
        left join fetch partItem.part
        where w.id = :id
        """)
    Optional<WorkOrder> findDetailedPartItemsByIdForStockUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"customer", "vehicle"})
    List<WorkOrder> findByStatus(WorkOrderStatus status);

    @Query("""
        select w
        from WorkOrder w
        where w.startedAt is not null
          and w.finishedAt is not null
        """)
    List<WorkOrder> findCompletedForMetrics();

    @Query("""
        select w
        from WorkOrder w
        where w.startedAt is not null
          and w.finishedAt is not null
          and w.finishedAt >= :from
        """)
    List<WorkOrder> findCompletedForMetricsFrom(@Param("from") OffsetDateTime from);

    @Query("""
        select w
        from WorkOrder w
        where w.startedAt is not null
          and w.finishedAt is not null
          and w.finishedAt <= :to
        """)
    List<WorkOrder> findCompletedForMetricsTo(@Param("to") OffsetDateTime to);

    @Query("""
        select w
        from WorkOrder w
        where w.startedAt is not null
          and w.finishedAt is not null
          and w.finishedAt between :from and :to
        """)
    List<WorkOrder> findCompletedForMetricsBetween(
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );
}
