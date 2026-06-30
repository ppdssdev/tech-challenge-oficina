package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerDomainTest {

    @Test
    void shouldNormalizeCustomerData() {
        var customer = new Customer(
            " Maria Silva ",
            DocumentType.CPF,
            "529.982.247-25",
            " maria@email.com ",
            "(31) 99999-9999"
        );

        assertThat(customer.getFullName()).isEqualTo("Maria Silva");
        assertThat(customer.getDocumentType()).isEqualTo(DocumentType.CPF);
        assertThat(customer.getDocumentNumber()).isEqualTo("52998224725");
        assertThat(customer.getEmail()).isEqualTo("maria@email.com");
        assertThat(customer.getPhone()).isEqualTo("31999999999");
    }

    @Test
    void shouldNormalizeBlankOptionalContactsToNull() {
        var customer = new Customer("Maria Silva", DocumentType.CPF, "52998224725", " ", null);

        assertThat(customer.getEmail()).isNull();
        assertThat(customer.getPhone()).isNull();
    }

    @Test
    void shouldRejectInvalidCustomerData() {
        assertThatThrownBy(() -> new Customer(" ", DocumentType.CPF, "52998224725", null, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Nome do cliente");

        assertThatThrownBy(() -> new Customer("Maria Silva", null, "52998224725", null, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Tipo do documento");

        assertThatThrownBy(() -> new Customer("Maria Silva", DocumentType.CPF, "52998224724", null, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Documento inválido");
    }
}
