package br.com.fiap.techchallenge.oficina.domain.model.workorder;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Entity
@Table(name = "work_orders")
public class WorkOrder extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

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
    private BigDecimal totalServices = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_parts", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalParts = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<WorkOrderServiceItem> serviceItems = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<WorkOrderPartItem> partItems = new ArrayList<>();

    protected WorkOrder() {
    }

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
