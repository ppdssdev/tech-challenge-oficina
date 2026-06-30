package br.com.fiap.techchallenge.oficina.domain.model.vehicle;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Year;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Entity
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Embedded
    @AttributeOverride(
        name = "value",
        column = @Column(name = "plate", nullable = false, unique = true, length = 7)
    )
    private VehiclePlate plate;

    @Column(nullable = false, length = 60)
    private String brand;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "manufacturing_year", nullable = false)
    private int manufacturingYear;

    protected Vehicle() {
    }

    public Vehicle(Customer customer, String plate, String brand, String model, int manufacturingYear) {
        update(customer, plate, brand, model, manufacturingYear);
    }

    public void update(Customer customer, String plate, String brand, String model, int manufacturingYear) {
        if (customer == null) {
            throw new BusinessException("Cliente do veículo é obrigatório.");
        }
        if (isBlank(brand) || isBlank(model)) {
            throw new BusinessException("Marca e modelo do veículo são obrigatórios.");
        }
        int currentYearPlusOne = Year.now().getValue() + 1;
        if (manufacturingYear < 1900 || manufacturingYear > currentYearPlusOne) {
            throw new BusinessException("Ano do veículo inválido.");
        }

        this.customer = customer;
        this.plate = new VehiclePlate(plate);
        this.brand = brand.trim();
        this.model = model.trim();
        this.manufacturingYear = manufacturingYear;
    }


    public Customer getCustomer() {
        return customer;
    }

    public String getPlate() {
        return plate.getValue();
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public int getManufacturingYear() {
        return manufacturingYear;
    }
}
