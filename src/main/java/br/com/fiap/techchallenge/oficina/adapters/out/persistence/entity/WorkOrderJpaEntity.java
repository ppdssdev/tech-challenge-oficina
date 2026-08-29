package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "WorkOrderJpaEntity")
@Table(name = "work_orders")
public class WorkOrderJpaEntity extends JpaBaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerJpaEntity customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleJpaEntity vehicle;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkOrderStatus status;
    @Column(name = "diagnostic_notes", length = 2000)
    private String diagnosticNotes;
    @Column(name = "customer_authorized_at")
    private OffsetDateTime customerAuthorizedAt;
    @Column(name = "started_at")
    private OffsetDateTime startedAt;
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;
    @Column(name = "total_services", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalServices;
    @Column(name = "total_parts", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalParts;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<WorkOrderServiceItemJpaEntity> serviceItems = new ArrayList<>();
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<WorkOrderPartItemJpaEntity> partItems = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public CustomerJpaEntity getCustomer() { return customer; }
    public void setCustomer(CustomerJpaEntity customer) { this.customer = customer; }
    public VehicleJpaEntity getVehicle() { return vehicle; }
    public void setVehicle(VehicleJpaEntity vehicle) { this.vehicle = vehicle; }
    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }
    public String getDiagnosticNotes() { return diagnosticNotes; }
    public void setDiagnosticNotes(String diagnosticNotes) { this.diagnosticNotes = diagnosticNotes; }
    public OffsetDateTime getCustomerAuthorizedAt() { return customerAuthorizedAt; }
    public void setCustomerAuthorizedAt(OffsetDateTime value) { customerAuthorizedAt = value; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime value) { startedAt = value; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime value) { finishedAt = value; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime value) { deliveredAt = value; }
    public BigDecimal getTotalServices() { return totalServices; }
    public void setTotalServices(BigDecimal value) { totalServices = value; }
    public BigDecimal getTotalParts() { return totalParts; }
    public void setTotalParts(BigDecimal value) { totalParts = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { totalAmount = value; }
    public List<WorkOrderServiceItemJpaEntity> getServiceItems() { return serviceItems; }
    public List<WorkOrderPartItemJpaEntity> getPartItems() { return partItems; }
}
