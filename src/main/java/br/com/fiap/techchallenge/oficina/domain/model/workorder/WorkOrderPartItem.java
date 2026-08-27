package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

public class WorkOrderPartItem extends BaseEntity {

    private final Part part;
    private String partName;
    private String sku;
    private BigDecimal unitPrice;
    private final int quantity;
    private BigDecimal lineTotal;
    private boolean stockReserved;

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

        this.part = part;
        this.partName = part.getName();
        this.sku = part.getSku();
        this.unitPrice = part.getUnitPrice();
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        this.stockReserved = false;
    }

    static WorkOrderPartItem restore(
        UUID id, WorkOrder workOrder, Part part, String partName, String sku,
        BigDecimal unitPrice, int quantity, BigDecimal lineTotal, boolean stockReserved,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var item = new WorkOrderPartItem(workOrder, part, quantity);
        item.partName = partName;
        item.sku = sku;
        item.unitPrice = unitPrice;
        item.lineTotal = lineTotal;
        item.stockReserved = stockReserved;
        item.restoreMetadata(id, createdAt, updatedAt);
        return item;
    }

    public void reserveStock() {
        if (!stockReserved) {
            part.decreaseStock(quantity);
            stockReserved = true;
        }
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
