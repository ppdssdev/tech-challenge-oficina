package br.com.fiap.techchallenge.oficina.domain.model.catalog;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Part extends BaseEntity {

    private String name;
    private String sku;
    private BigDecimal unitPrice;
    private int quantityInStock;
    private int minimumStock;
    private boolean active = true;
    private long version;

    public Part(String name, String sku, BigDecimal unitPrice, int quantityInStock, int minimumStock) {
        update(name, sku, unitPrice, quantityInStock, minimumStock, true);
    }

    public static Part restore(
        UUID id, String name, String sku, BigDecimal unitPrice, int quantityInStock,
        int minimumStock, boolean active, long version, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var part = new Part(name, sku, unitPrice, quantityInStock, minimumStock);
        part.active = active;
        part.version = version;
        part.restoreMetadata(id, createdAt, updatedAt);
        return part;
    }

    public void update(String name, String sku, BigDecimal unitPrice, int quantityInStock, int minimumStock, boolean active) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Nome da peça/insumo é obrigatório.");
        }
        if (sku == null || sku.isBlank()) {
            throw new BusinessException("SKU da peça/insumo é obrigatório.");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Preço unitário da peça não pode ser negativo.");
        }
        if (quantityInStock < 0 || minimumStock < 0) {
            throw new BusinessException("Estoque e estoque mínimo não podem ser negativos.");
        }

        this.name = name.trim();
        this.sku = sku.trim().toUpperCase();
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.quantityInStock = quantityInStock;
        this.minimumStock = minimumStock;
        this.active = active;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantidade a baixar deve ser maior que zero.");
        }
        if (quantityInStock < quantity) {
            throw new BusinessException("Estoque insuficiente para a peça " + sku + ".");
        }
        quantityInStock -= quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantidade a adicionar deve ser maior que zero.");
        }
        quantityInStock += quantity;
    }

    public boolean isBelowMinimumStock() {
        return quantityInStock <= minimumStock;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
