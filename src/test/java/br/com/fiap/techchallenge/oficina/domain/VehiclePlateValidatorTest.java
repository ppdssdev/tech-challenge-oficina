package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.service.VehiclePlateValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehiclePlateValidatorTest {

    @Test
    void shouldValidateOldAndMercosurPlates() {
        assertThat(VehiclePlateValidator.isValid("ABC-1234")).isTrue();
        assertThat(VehiclePlateValidator.isValid("ABC1D23")).isTrue();
        assertThat(VehiclePlateValidator.isValid("AB12345")).isFalse();
    }

    @Test
    void shouldNormalizePlate() {
        assertThat(VehiclePlateValidator.normalize("abc-1d23")).isEqualTo("ABC1D23");
    }
}
