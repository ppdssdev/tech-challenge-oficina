package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "work_order_part_items")
public class WorkOrderPartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(name = "part_name", nullable = false, length = 120)
    private String partName;

    @Column(name = "sku", nullable = false, length = 40)
    private String sku;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved;

    protected WorkOrderPartItem() {
    }

    public WorkOrderPartItem(WorkOrder workOrder, Part part, int quantity) {
        if (workOrder == null || part == null) {
            throw new BusinessException("Ordem de serviço e peça são obrigatórias.");
        }
        if (!part.isActive()) {
            throw new BusinessException("Peça inativa não pode ser incluída na OS.");
        }
        if (quantity <= 0) {
            throw new BusinessException("Quantidade da peça deve ser maior que zero.");
        }
        if (part.getQuantityInStock() < quantity) {
            throw new BusinessException("Estoque insuficiente para a peça " + part.getSku() + ".");
        }

        this.workOrder = workOrder;
        this.part = part;
        this.partName = part.getName();
        this.sku = part.getSku();
        this.unitPrice = part.getUnitPrice();
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        this.stockReserved = false;
    }

    public void reserveStock() {
        if (!stockReserved) {
            part.decreaseStock(quantity);
            stockReserved = true;
        }
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public Part getPart() {
        return part;
    }

    public String getPartName() {
        return partName;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public boolean isStockReserved() {
        return stockReserved;
    }
}
