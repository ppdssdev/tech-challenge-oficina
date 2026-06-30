package br.com.fiap.techchallenge.oficina.domain.model.catalog;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "service_catalog_items")
public class ServiceCatalogItem extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Column(nullable = false)
    private boolean active = true;

    protected ServiceCatalogItem() {
    }

    public ServiceCatalogItem(String name, String description, BigDecimal basePrice, int estimatedMinutes) {
        update(name, description, basePrice, estimatedMinutes, true);
    }

    public void update(String name, String description, BigDecimal basePrice, int estimatedMinutes, boolean active) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Nome do serviço é obrigatório.");
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Preço base do serviço não pode ser negativo.");
        }
        if (estimatedMinutes <= 0) {
            throw new BusinessException("Tempo estimado do serviço deve ser maior que zero.");
        }

        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.basePrice = basePrice.setScale(2, RoundingMode.HALF_UP);
        this.estimatedMinutes = estimatedMinutes;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public boolean isActive() {
        return active;
    }
}
