package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.api.controller.ApiExceptionHandler;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

    @Test
    void shouldReturnExpectedStatusForDomainAndSecurityExceptions() {
        assertThat(handler.handleBusiness(new BusinessException("Regra inválida."), request).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(handler.handleBadCredentials(new BadCredentialsException("bad"), request).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("denied"), request).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleNotFound(new NotFoundException("Não encontrado."), request).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleConflict(new ConflictException("Conflito."), request).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
