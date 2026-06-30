package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentValidatorTest {

    @Test
    void shouldValidateCpf() {
        assertThat(DocumentValidator.isValid(DocumentType.CPF, "529.982.247-25")).isTrue();
        assertThat(DocumentValidator.isValid(DocumentType.CPF, "111.111.111-11")).isFalse();
        assertThat(DocumentValidator.isValid(DocumentType.CPF, "52998224724")).isFalse();
    }

    @Test
    void shouldValidateCnpj() {
        assertThat(DocumentValidator.isValid(DocumentType.CNPJ, "11.222.333/0001-81")).isTrue();
        assertThat(DocumentValidator.isValid(DocumentType.CNPJ, "11.111.111/1111-11")).isFalse();
        assertThat(DocumentValidator.isValid(DocumentType.CNPJ, "11222333000180")).isFalse();
    }

    @Test
    void shouldNormalizeOnlyDigits() {
        assertThat(DocumentValidator.onlyDigits("529.982.247-25")).isEqualTo("52998224725");
    }
}
