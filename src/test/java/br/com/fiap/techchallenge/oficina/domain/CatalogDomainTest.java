package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogDomainTest {

    @Test
    void shouldNormalizeAndUpdatePart() {
        var part = new Part(" Filtro de óleo ", " filter-001 ", BigDecimal.valueOf(35.555), 10, 2);

        part.update(" Filtro premium ", " filter-002 ", BigDecimal.valueOf(40), 4, 5, false);

        assertThat(part.getName()).isEqualTo("Filtro premium");
        assertThat(part.getSku()).isEqualTo("FILTER-002");
        assertThat(part.getUnitPrice()).isEqualByComparingTo("40.00");
        assertThat(part.getQuantityInStock()).isEqualTo(4);
        assertThat(part.getMinimumStock()).isEqualTo(5);
        assertThat(part.isBelowMinimumStock()).isTrue();
        assertThat(part.isActive()).isFalse();
        assertThat(part.getVersion()).isZero();
    }

    @Test
    void shouldRejectInvalidPartData() {
        assertThatThrownBy(() -> new Part(" ", "SKU-1", BigDecimal.TEN, 1, 1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Nome da peça");
        assertThatThrownBy(() -> new Part("Filtro", " ", BigDecimal.TEN, 1, 1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("SKU");
        assertThatThrownBy(() -> new Part("Filtro", "SKU-1", BigDecimal.valueOf(-1), 1, 1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não pode ser negativo");
        assertThatThrownBy(() -> new Part("Filtro", "SKU-1", BigDecimal.TEN, -1, 1))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não podem ser negativos");
    }

    @Test
    void shouldNormalizeAndUpdateServiceCatalogItem() {
        var service = new ServiceCatalogItem(" Troca de óleo ", " Serviço básico ", BigDecimal.valueOf(120.555), 40);

        service.update(" Diagnóstico ", " ", BigDecimal.valueOf(180), 45, false);

        assertThat(service.getName()).isEqualTo("Diagnóstico");
        assertThat(service.getDescription()).isNull();
        assertThat(service.getBasePrice()).isEqualByComparingTo("180.00");
        assertThat(service.getEstimatedMinutes()).isEqualTo(45);
        assertThat(service.isActive()).isFalse();
    }

    @Test
    void shouldRejectInvalidServiceCatalogData() {
        assertThatThrownBy(() -> new ServiceCatalogItem(" ", null, BigDecimal.TEN, 10))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Nome do serviço");
        assertThatThrownBy(() -> new ServiceCatalogItem("Troca", null, BigDecimal.valueOf(-1), 10))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não pode ser negativo");
        assertThatThrownBy(() -> new ServiceCatalogItem("Troca", null, BigDecimal.TEN, 0))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Tempo estimado");
    }
}
