package br.com.fiap.techchallenge.oficina.domain.model.vehicle;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.service.VehiclePlateValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class VehiclePlate {

    @Column(nullable = false, length = 7)
    private String value;

    protected VehiclePlate() {
    }

    public VehiclePlate(String value) {
        String normalizedValue = VehiclePlateValidator.normalize(value);
        if (!VehiclePlateValidator.isValid(normalizedValue)) {
            throw new BusinessException("Placa do veículo inválida.");
        }
        this.value = normalizedValue;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VehiclePlate that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
