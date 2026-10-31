package br.com.fiap.techchallenge.oficina.adapters.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void shouldMaskFormattedAndUnformattedCpfAndCnpj() {
        assertThat(PiiMasker.mask("CPF 529.982.247-25 e 52998224725"))
            .isEqualTo("CPF 529.***.***-25 e 529.***.***-25");
        assertThat(PiiMasker.mask("CNPJ 11.222.333/0001-81 e 11222333000181"))
            .isEqualTo("CNPJ 11.***.***/****-81 e 11.***.***/****-81");
    }
}
