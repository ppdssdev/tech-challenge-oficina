package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WorkOrder extends BaseEntity {

    private final String code;
    private final Customer customer;
    private final Vehicle vehicle;
    private WorkOrderStatus status;
    private String diagnosticNotes;
    private OffsetDateTime customerAuthorizedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime deliveredAt;
    private BigDecimal totalServices = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private BigDecimal totalParts = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final List<WorkOrderServiceItem> serviceItems = new ArrayList<>();
    private final List<WorkOrderPartItem> partItems = new ArrayList<>();

    public WorkOrder(String code, Customer customer, Vehicle vehicle, String diagnosticNotes) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Código da ordem de serviço é obrigatório.");
        }
        if (customer == null || vehicle == null) {
            throw new BusinessException("Cliente e veículo são obrigatórios para criar uma OS.");
        }
        if (!vehicle.getCustomer().getDocumentNumber().equals(customer.getDocumentNumber())) {
            throw new BusinessException("O veículo informado não pertence ao cliente da OS.");
        }
        this.code = code;
        this.customer = customer;
        this.vehicle = vehicle;
        this.status = WorkOrderStatus.RECEIVED;
        this.diagnosticNotes = normalize(diagnosticNotes);
    }

    public static WorkOrder restore(
        UUID id,
        String code,
        Customer customer,
        Vehicle vehicle,
        WorkOrderStatus status,
        String diagnosticNotes,
        OffsetDateTime customerAuthorizedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime deliveredAt,
        BigDecimal totalServices,
        BigDecimal totalParts,
        BigDecimal totalAmount,
        List<ServiceItemSnapshot> services,
        List<PartItemSnapshot> parts,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        var order = new WorkOrder(code, customer, vehicle, diagnosticNotes);
        order.status = status;
        order.customerAuthorizedAt = customerAuthorizedAt;
        order.startedAt = startedAt;
        order.finishedAt = finishedAt;
        order.deliveredAt = deliveredAt;
        order.totalServices = totalServices;
        order.totalParts = totalParts;
        order.totalAmount = totalAmount;
        order.restoreMetadata(id, createdAt, updatedAt);
        services.forEach(snapshot -> order.serviceItems.add(WorkOrderServiceItem.restore(
            snapshot.id(), order, snapshot.service(), snapshot.serviceName(), snapshot.unitPrice(),
            snapshot.quantity(), snapshot.estimatedMinutes(), snapshot.lineTotal(), snapshot.createdAt(), snapshot.updatedAt()
        )));
        parts.forEach(snapshot -> order.partItems.add(WorkOrderPartItem.restore(
            snapshot.id(), order, snapshot.part(), snapshot.partName(), snapshot.sku(), snapshot.unitPrice(),
            snapshot.quantity(), snapshot.lineTotal(), snapshot.stockReserved(), snapshot.createdAt(), snapshot.updatedAt()
        )));
        return order;
    }

    public record ServiceItemSnapshot(
        UUID id, ServiceCatalogItem service, String serviceName, BigDecimal unitPrice, int quantity,
        int estimatedMinutes, BigDecimal lineTotal, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
    }

    public record PartItemSnapshot(
        UUID id, Part part, String partName, String sku, BigDecimal unitPrice, int quantity,
        BigDecimal lineTotal, boolean stockReserved, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
    }

    public void addRequestedService(ServiceCatalogItem service, int quantity) {
        ensureCanChangeBudget();
        serviceItems.add(new WorkOrderServiceItem(this, service, quantity));
        recalculateTotals();
        sendBudgetForApprovalIfNeeded();
    }

    public void addRequiredPart(Part part, int quantity) {
        ensureCanChangeBudget();
        partItems.add(new WorkOrderPartItem(this, part, quantity));
        recalculateTotals();
        sendBudgetForApprovalIfNeeded();
    }

    public void startDiagnosis(String notes) {
        if (status != WorkOrderStatus.RECEIVED) {
            throw new BusinessException("Apenas OS recebida pode entrar em diagnóstico.");
        }
        this.status = WorkOrderStatus.IN_DIAGNOSIS;
        this.diagnosticNotes = normalize(notes);
    }

    public void updateDiagnosticNotes(String notes) {
        this.diagnosticNotes = normalize(notes);
        if (status == WorkOrderStatus.RECEIVED) {
            this.status = WorkOrderStatus.IN_DIAGNOSIS;
        }
    }

    public void sendBudgetForApprovalIfNeeded() {
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0
            && (status == WorkOrderStatus.RECEIVED || status == WorkOrderStatus.IN_DIAGNOSIS || status == WorkOrderStatus.IN_EXECUTION)) {
            status = WorkOrderStatus.WAITING_APPROVAL;
        }
    }

    public void approveBudget() {
        if (status != WorkOrderStatus.WAITING_APPROVAL) {
            throw new BusinessException("Somente OS aguardando aprovação pode ser aprovada.");
        }
        reservePendingPartsStock();
        this.customerAuthorizedAt = OffsetDateTime.now();
        this.status = WorkOrderStatus.IN_EXECUTION;
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    public void rejectBudget() {
        if (status != WorkOrderStatus.WAITING_APPROVAL) {
            throw new BusinessException("Somente OS aguardando aprovação pode ter o orçamento recusado.");
        }
        if (startedAt != null) {
            throw new BusinessException("Recusa de orçamento adicional em OS já iniciada não é suportada nesta etapa.");
        }
        this.status = WorkOrderStatus.BUDGET_REJECTED;
    }

    public void finish() {
        if (status != WorkOrderStatus.IN_EXECUTION) {
            throw new BusinessException("Somente OS em execução pode ser finalizada.");
        }
        this.status = WorkOrderStatus.FINALIZED;
        this.finishedAt = OffsetDateTime.now();
    }

    public void deliver() {
        if (status != WorkOrderStatus.FINALIZED) {
            throw new BusinessException("Somente OS finalizada pode ser entregue.");
        }
        this.status = WorkOrderStatus.DELIVERED;
        this.deliveredAt = OffsetDateTime.now();
    }

    public Long executionMinutes() {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMinutes();
    }

    private void reservePendingPartsStock() {
        var pendingItems = partItems.stream()
            .filter(item -> !item.isStockReserved())
            .toList();

        ensureStockAvailableForPendingItems(pendingItems);
        pendingItems.forEach(WorkOrderPartItem::reserveStock);
    }

    private void ensureStockAvailableForPendingItems(List<WorkOrderPartItem> pendingItems) {
        Map<Part, Integer> quantitiesByPart = pendingItems.stream()
            .collect(Collectors.groupingBy(
                WorkOrderPartItem::getPart,
                LinkedHashMap::new,
                Collectors.summingInt(WorkOrderPartItem::getQuantity)
            ));

        quantitiesByPart.forEach((part, requiredQuantity) -> {
            if (part.getQuantityInStock() < requiredQuantity) {
                throw new BusinessException("Estoque insuficiente para a peça " + part.getSku() + ".");
            }
        });
    }

    private void ensureCanChangeBudget() {
        if (status == WorkOrderStatus.BUDGET_REJECTED) {
            throw new BusinessException("Não é possível alterar orçamento de OS com orçamento recusado.");
        }
        if (status == WorkOrderStatus.FINALIZED || status == WorkOrderStatus.DELIVERED) {
            throw new BusinessException("Não é possível alterar orçamento de OS finalizada ou entregue.");
        }
    }

    private void recalculateTotals() {
        totalServices = serviceItems.stream()
            .map(WorkOrderServiceItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        totalParts = partItems.stream()
            .map(WorkOrderPartItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        totalAmount = totalServices.add(totalParts).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getCode() {
        return code;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public String getDiagnosticNotes() {
        return diagnosticNotes;
    }

    public OffsetDateTime getCustomerAuthorizedAt() {
        return customerAuthorizedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public BigDecimal getTotalServices() {
        return totalServices;
    }

    public BigDecimal getTotalParts() {
        return totalParts;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<WorkOrderServiceItem> getServiceItems() {
        return Collections.unmodifiableList(serviceItems);
    }

    public List<WorkOrderPartItem> getPartItems() {
        return Collections.unmodifiableList(partItems);
    }
}
