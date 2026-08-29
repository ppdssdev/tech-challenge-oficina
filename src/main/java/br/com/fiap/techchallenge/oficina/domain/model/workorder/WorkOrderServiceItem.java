package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

public class WorkOrderServiceItem extends BaseEntity {

    private final ServiceCatalogItem service;
    private final String serviceName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final int estimatedMinutes;
    private final BigDecimal lineTotal;

    public WorkOrderServiceItem(WorkOrder workOrder, ServiceCatalogItem service, int quantity) {
        validateStructure(workOrder, service, quantity);
        if (!service.isActive()) {
            throw new BusinessException("Serviço inativo não pode ser incluído na OS.");
        }

        this.service = service;
        this.serviceName = service.getName();
        this.unitPrice = service.getBasePrice();
        this.quantity = quantity;
        this.estimatedMinutes = service.getEstimatedMinutes() * quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private WorkOrderServiceItem(
        WorkOrder workOrder,
        ServiceCatalogItem service,
        String serviceName,
        BigDecimal unitPrice,
        int quantity,
        int estimatedMinutes,
        BigDecimal lineTotal
    ) {
        validateStructure(workOrder, service, quantity);
        if (unitPrice == null || lineTotal == null) {
            throw new BusinessException("Preço unitário e total do serviço são obrigatórios.");
        }

        this.service = service;
        this.serviceName = serviceName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.estimatedMinutes = estimatedMinutes;
        this.lineTotal = lineTotal;
    }

    static WorkOrderServiceItem restore(
        UUID id, WorkOrder workOrder, ServiceCatalogItem service, String serviceName,
        BigDecimal unitPrice, int quantity, int estimatedMinutes, BigDecimal lineTotal,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var item = new WorkOrderServiceItem(
            workOrder, service, serviceName, unitPrice, quantity, estimatedMinutes, lineTotal
        );
        item.restoreMetadata(id, createdAt, updatedAt);
        return item;
    }

    private static void validateStructure(
        WorkOrder workOrder,
        ServiceCatalogItem service,
        int quantity
    ) {
        if (workOrder == null || service == null) {
            throw new BusinessException("Ordem de serviço e serviço são obrigatórios.");
        }
        if (quantity <= 0) {
            throw new BusinessException("Quantidade do serviço deve ser maior que zero.");
        }
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
