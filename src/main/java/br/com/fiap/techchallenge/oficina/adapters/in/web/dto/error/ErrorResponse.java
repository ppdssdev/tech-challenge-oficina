package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.error;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    String traceId,
    Map<String, String> fields
) {
}
