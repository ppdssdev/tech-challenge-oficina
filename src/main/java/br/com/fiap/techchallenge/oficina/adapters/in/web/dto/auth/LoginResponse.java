package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.auth;

public record LoginResponse(
    String tokenType,
    String accessToken,
    long expiresInMinutes
) {
}
