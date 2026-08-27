package br.com.fiap.techchallenge.oficina.domain.model.catalog;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ServiceCatalogItem extends BaseEntity {

    private String name;
    private String description;
    private BigDecimal basePrice;
    private int estimatedMinutes;
    private boolean active = true;

    public ServiceCatalogItem(String name, String description, BigDecimal basePrice, int estimatedMinutes) {
        update(name, description, basePrice, estimatedMinutes, true);
    }

    public static ServiceCatalogItem restore(
        UUID id, String name, String description, BigDecimal basePrice, int estimatedMinutes,
        boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var item = new ServiceCatalogItem(name, description, basePrice, estimatedMinutes);
        item.active = active;
        item.restoreMetadata(id, createdAt, updatedAt);
        return item;
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
