package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.adapters.in.web.ApiExceptionHandler;
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"52998224725", "11.222.333/0001-81", "arbitrary-pii", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void replacesUntrustedCorrelationIds(String value) {
        request.addHeader("X-Correlation-Id", value);
        var body = handler.handleUnexpected(new RuntimeException("secret"), request).getBody();
        assertThat(body.traceId()).isNotEqualTo(value);
        assertThat(java.util.UUID.fromString(body.traceId()).version()).isEqualTo(4);
    }

    @Test
    void keepsValidCorrelationAndOmitsSensitiveLogData() {
        String id = "e53eeffe-36c7-46bb-a545-1a2056d243bf";
        request.addHeader("X-Correlation-Id", id);
        request.setRequestURI("/api/52998224725/11.222.333/0001-81");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
        var diagnostic = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("oficina.restricted.diagnostics");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        diagnostic.addAppender(appender);
        try {
            var body = handler.handleUnexpected(new RuntimeException("52998224725 credential=private"), request).getBody();
            handler.handleBusiness(new BusinessException("11222333000181 credential=private"), request);
            assertThat(body.traceId()).isEqualTo(id);
            assertThat(body.path()).isEqualTo("[unmatched]");
            assertThat(appender.list).hasSize(3).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("52998224725", "11222333000181", "private");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(appender.list.get(1).getFormattedMessage()).contains("frames=", "ApiExceptionHandlerTest");
        } finally {
            logger.detachAppender(appender);
            diagnostic.detachAppender(appender);
        }
    }

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
