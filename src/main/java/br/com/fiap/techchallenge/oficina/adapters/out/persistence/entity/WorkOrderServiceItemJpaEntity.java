package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity(name = "WorkOrderServiceItemJpaEntity")
@Table(name = "work_order_service_items")
public class WorkOrderServiceItemJpaEntity extends JpaBaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrderJpaEntity workOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceCatalogJpaEntity service;
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public WorkOrderJpaEntity getWorkOrder() { return workOrder; }
    public void setWorkOrder(WorkOrderJpaEntity value) { workOrder = value; }
    public ServiceCatalogJpaEntity getService() { return service; }
    public void setService(ServiceCatalogJpaEntity value) { service = value; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String value) { serviceName = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int value) { estimatedMinutes = value; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal value) { lineTotal = value; }
}
