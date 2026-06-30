package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "work_order_service_items")
public class WorkOrderServiceItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceCatalogItem service;

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

    protected WorkOrderServiceItem() {
    }

    public WorkOrderServiceItem(WorkOrder workOrder, ServiceCatalogItem service, int quantity) {
        if (workOrder == null || service == null) {
            throw new BusinessException("Ordem de serviço e serviço são obrigatórios.");
        }
        if (!service.isActive()) {
            throw new BusinessException("Serviço inativo não pode ser incluído na OS.");
        }
        if (quantity <= 0) {
            throw new BusinessException("Quantidade do serviço deve ser maior que zero.");
        }

        this.workOrder = workOrder;
        this.service = service;
        this.serviceName = service.getName();
        this.unitPrice = service.getBasePrice();
        this.quantity = quantity;
        this.estimatedMinutes = service.getEstimatedMinutes() * quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public ServiceCatalogItem getService() {
        return service;
    }

    public String getServiceName() {
        return serviceName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
