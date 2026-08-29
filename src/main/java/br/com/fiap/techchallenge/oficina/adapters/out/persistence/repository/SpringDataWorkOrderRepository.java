package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.WorkOrderJpaEntity;
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

public interface SpringDataWorkOrderRepository extends JpaRepository<WorkOrderJpaEntity, UUID> {
    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"customer", "vehicle", "vehicle.customer"})
    Optional<WorkOrderJpaEntity> findByCode(String code);

    @EntityGraph(attributePaths = {"customer", "vehicle", "vehicle.customer"})
    @Query("select w from WorkOrderJpaEntity w where w.id = :id")
    Optional<WorkOrderJpaEntity> findDetailedById(@Param("id") UUID id);

    @Query("select distinct w from WorkOrderJpaEntity w left join fetch w.serviceItems i left join fetch i.service where w.id = :id")
    Optional<WorkOrderJpaEntity> loadServiceItems(@Param("id") UUID id);

    @Query("select distinct w from WorkOrderJpaEntity w left join fetch w.partItems i left join fetch i.part where w.id = :id")
    Optional<WorkOrderJpaEntity> loadPartItems(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkOrderJpaEntity w where w.id = :id")
    Optional<WorkOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"customer", "vehicle", "vehicle.customer"})
    List<WorkOrderJpaEntity> findByStatus(WorkOrderStatus status);

    @Override
    @EntityGraph(attributePaths = {"customer", "vehicle", "vehicle.customer"})
    List<WorkOrderJpaEntity> findAll();

    @Query("select w from WorkOrderJpaEntity w where w.startedAt is not null and w.finishedAt is not null")
    List<WorkOrderJpaEntity> findCompletedForMetrics();

    @Query("select w from WorkOrderJpaEntity w where w.startedAt is not null and w.finishedAt is not null and w.finishedAt >= :from")
    List<WorkOrderJpaEntity> findCompletedForMetricsFrom(@Param("from") OffsetDateTime from);

    @Query("select w from WorkOrderJpaEntity w where w.startedAt is not null and w.finishedAt is not null and w.finishedAt <= :to")
    List<WorkOrderJpaEntity> findCompletedForMetricsTo(@Param("to") OffsetDateTime to);

    @Query("select w from WorkOrderJpaEntity w where w.startedAt is not null and w.finishedAt is not null and w.finishedAt between :from and :to")
    List<WorkOrderJpaEntity> findCompletedForMetricsBetween(
        @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to
    );
}
