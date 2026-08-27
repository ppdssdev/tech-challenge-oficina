package br.com.fiap.techchallenge.oficina.domain.model.vehicle;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.UUID;

public class Vehicle extends BaseEntity {

    private Customer customer;
    private VehiclePlate plate;
    private String brand;
    private String model;
    private int manufacturingYear;

    public Vehicle(Customer customer, String plate, String brand, String model, int manufacturingYear) {
        update(customer, plate, brand, model, manufacturingYear);
    }

    public static Vehicle restore(
        UUID id, Customer customer, String plate, String brand, String model, int manufacturingYear,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var vehicle = new Vehicle(customer, plate, brand, model, manufacturingYear);
        vehicle.restoreMetadata(id, createdAt, updatedAt);
        return vehicle;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
