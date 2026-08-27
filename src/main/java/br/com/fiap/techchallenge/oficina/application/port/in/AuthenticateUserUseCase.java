package br.com.fiap.techchallenge.oficina.application.port.in;

public interface AuthenticateUserUseCase {
    Token authenticate(String username, String password);

    record Token(String tokenType, String accessToken, long expiresInMinutes) {
    }
}
