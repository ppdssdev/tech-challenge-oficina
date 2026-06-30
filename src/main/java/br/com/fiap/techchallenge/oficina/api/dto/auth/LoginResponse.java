package br.com.fiap.techchallenge.oficina.api.dto.auth;

public record LoginResponse(
    String tokenType,
    String accessToken,
    long expiresInMinutes
) {
}
