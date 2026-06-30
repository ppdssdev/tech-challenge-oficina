package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartInventoryTest {

    @Test
    void shouldDecreaseAndIncreaseStock() {
        var part = new Part("Filtro de óleo", "filter-001", BigDecimal.valueOf(35), 10, 2);

        part.decreaseStock(3);
        part.increaseStock(1);

        assertThat(part.getQuantityInStock()).isEqualTo(8);
        assertThat(part.isBelowMinimumStock()).isFalse();
    }

    @Test
    void shouldRejectStockDecreaseWhenQuantityIsInsufficient() {
        var part = new Part("Filtro de óleo", "filter-001", BigDecimal.valueOf(35), 2, 2);

        assertThatThrownBy(() -> part.decreaseStock(3))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void shouldIdentifyBelowMinimumStock() {
        var part = new Part("Filtro de óleo", "filter-001", BigDecimal.valueOf(35), 2, 2);

        assertThat(part.isBelowMinimumStock()).isTrue();
    }
}
