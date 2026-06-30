package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentNumber;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.VehiclePlate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectTest {

    @Test
    void shouldNormalizeAndCompareDocumentNumbersByValue() {
        var formatted = new DocumentNumber(DocumentType.CPF, "529.982.247-25");
        var digitsOnly = new DocumentNumber(DocumentType.CPF, "52998224725");

        assertThat(formatted.getType()).isEqualTo(DocumentType.CPF);
        assertThat(formatted.getValue()).isEqualTo("52998224725");
        assertThat(formatted).isEqualTo(digitsOnly);
        assertThat(formatted).hasToString("52998224725");
    }

    @Test
    void shouldRejectInvalidDocumentNumber() {
        assertThatThrownBy(() -> new DocumentNumber(DocumentType.CPF, "52998224724"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Documento inválido");
    }

    @Test
    void shouldNormalizeAndCompareVehiclePlatesByValue() {
        var formatted = new VehiclePlate("abc-1d23");
        var normalized = new VehiclePlate("ABC1D23");

        assertThat(formatted.getValue()).isEqualTo("ABC1D23");
        assertThat(formatted).isEqualTo(normalized);
        assertThat(formatted).hasToString("ABC1D23");
    }

    @Test
    void shouldRejectInvalidVehiclePlate() {
        assertThatThrownBy(() -> new VehiclePlate("AB12345"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Placa do veículo inválida");
    }
}
