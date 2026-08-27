package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.error.ErrorResponse;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@SuppressWarnings("unused") // Os métodos @ExceptionHandler são invocados pelo Spring MVC via reflexão.
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos.", request, null, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Acesso negado.", request, null, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();

        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fields.put(fieldError.getField(), fieldError.getDefaultMessage())
        );

        ex.getBindingResult().getGlobalErrors().forEach(globalError ->
                fields.put(globalError.getObjectName(), globalError.getDefaultMessage())
        );

        return error(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos.", request, fields, ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();
        var message = "JSON inválido ou corpo da requisição incompatível.";

        Throwable root = rootCause(ex);

        if (root instanceof InvalidFormatException invalidFormatException) {
            String fieldName = fieldPath(invalidFormatException);
            Class<?> targetType = invalidFormatException.getTargetType();
            Object rejectedValue = invalidFormatException.getValue();

            if (targetType != null && targetType.isEnum()) {
                fields.put(
                        fieldName,
                        "Valor '" + rejectedValue + "' inválido. Valores aceitos: " + enumValues(targetType)
                );
                message = "Campo com valor inválido.";
            } else {
                fields.put(
                        fieldName,
                        "Valor '" + rejectedValue + "' incompatível com o tipo esperado: " + typeName(targetType)
                );
                message = "Campo com tipo incompatível.";
            }
        }

        return error(HttpStatus.BAD_REQUEST, message, request, fields.isEmpty() ? null : fields, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();

        Class<?> requiredType = ex.getRequiredType();

        if (requiredType != null && requiredType.isEnum()) {
            fields.put(
                    ex.getName(),
                    "Valor '" + ex.getValue() + "' inválido. Valores aceitos: " + enumValues(requiredType)
            );
        } else {
            fields.put(
                    ex.getName(),
                    "Valor '" + ex.getValue() + "' incompatível com o tipo esperado: " + typeName(requiredType)
            );
        }

        return error(HttpStatus.BAD_REQUEST, "Parâmetro com valor inválido.", request, fields, ex);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();
        fields.put(ex.getParameterName(), "Parâmetro obrigatório não informado.");

        return error(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente.", request, fields, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();

        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath() == null
                    ? "request"
                    : violation.getPropertyPath().toString();

            fields.put(field, violation.getMessage());
        });

        return error(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos.", request, fields, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                "Registro viola uma regra de integridade. Verifique se já existe cadastro com os mesmos dados únicos.",
                request,
                null,
                ex
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return error(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Método HTTP não permitido para este recurso.",
                request,
                null,
                ex
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno inesperado. Informe o traceId ao suporte para análise.",
                request,
                null,
                ex
        );
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fields,
            Exception ex
    ) {
        String traceId = traceId(request);

        if (status.is5xxServerError()) {
            log.error(
                    "Erro inesperado na API. traceId={} status={} path={} message={}",
                    traceId,
                    status.value(),
                    request.getRequestURI(),
                    message,
                    ex
            );
        } else {
            log.warn(
                    "Erro tratado na API. traceId={} status={} path={} exception={} message={}",
                    traceId,
                    status.value(),
                    request.getRequestURI(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
        }

        return ResponseEntity.status(status).body(new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                traceId,
                fields
        ));
    }

    private String traceId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");

        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }

        return UUID.randomUUID().toString();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;

        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }

        return result;
    }

    private String fieldPath(InvalidFormatException ex) {
        String path = ex.getPath()
                .stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));

        return path.isBlank() ? "body" : path;
    }

    private String enumValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    private String typeName(Class<?> type) {
        return type == null ? "desconhecido" : type.getSimpleName();
    }
}
