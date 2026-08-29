package br.com.fiap.techchallenge.oficina.application.port.out;

public interface CredentialVerifierPort {
    AuthenticatedUser verify(String username, String password);

    record AuthenticatedUser(String username) {
    }
}
