package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity(name = "VehicleJpaEntity")
@Table(name = "vehicles")
public class VehicleJpaEntity extends JpaBaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerJpaEntity customer;
    @Column(nullable = false, unique = true, length = 7)
    private String plate;
    @Column(nullable = false, length = 60)
    private String brand;
    @Column(nullable = false, length = 80)
    private String model;
    @Column(name = "manufacturing_year", nullable = false)
    private int manufacturingYear;

    public CustomerJpaEntity getCustomer() { return customer; }
    public void setCustomer(CustomerJpaEntity customer) { this.customer = customer; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getManufacturingYear() { return manufacturingYear; }
    public void setManufacturingYear(int manufacturingYear) { this.manufacturingYear = manufacturingYear; }
}
