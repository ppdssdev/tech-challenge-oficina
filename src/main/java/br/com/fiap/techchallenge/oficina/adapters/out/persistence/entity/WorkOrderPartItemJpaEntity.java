package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity(name = "WorkOrderPartItemJpaEntity")
@Table(name = "work_order_part_items")
public class WorkOrderPartItemJpaEntity extends JpaBaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrderJpaEntity workOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private PartJpaEntity part;
    @Column(name = "part_name", nullable = false, length = 120)
    private String partName;
    @Column(nullable = false, length = 40)
    private String sku;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved;

    public WorkOrderJpaEntity getWorkOrder() { return workOrder; }
    public void setWorkOrder(WorkOrderJpaEntity value) { workOrder = value; }
    public PartJpaEntity getPart() { return part; }
    public void setPart(PartJpaEntity value) { part = value; }
    public String getPartName() { return partName; }
    public void setPartName(String value) { partName = value; }
    public String getSku() { return sku; }
    public void setSku(String value) { sku = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal value) { lineTotal = value; }
    public boolean isStockReserved() { return stockReserved; }
    public void setStockReserved(boolean value) { stockReserved = value; }
}
