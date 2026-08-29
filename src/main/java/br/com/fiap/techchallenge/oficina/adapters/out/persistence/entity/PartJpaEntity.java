package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity(name = "PartJpaEntity")
@Table(name = "parts")
public class PartJpaEntity extends JpaBaseEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, unique = true, length = 40)
    private String sku;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "quantity_in_stock", nullable = false)
    private int quantityInStock;
    @Column(name = "minimum_stock", nullable = false)
    private int minimumStock;
    @Column(nullable = false)
    private boolean active;
    @Version
    @Column(nullable = false)
    private long version;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public int getQuantityInStock() { return quantityInStock; }
    public void setQuantityInStock(int quantityInStock) { this.quantityInStock = quantityInStock; }
    public int getMinimumStock() { return minimumStock; }
    public void setMinimumStock(int minimumStock) { this.minimumStock = minimumStock; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
}
