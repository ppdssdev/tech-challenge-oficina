package br.com.fiap.techchallenge.oficina.domain;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    @Test
    void shouldPreserveDomainExceptionMessages() {
        assertThat(new BusinessException("Regra de negócio violada."))
            .hasMessage("Regra de negócio violada.");
        assertThat(new ConflictException("Registro duplicado."))
            .hasMessage("Registro duplicado.");
        assertThat(new NotFoundException("Registro não encontrado."))
            .hasMessage("Registro não encontrado.");
    }
}
