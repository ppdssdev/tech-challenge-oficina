package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.application.port.out.CredentialVerifierPort.AuthenticatedUser;

public interface TokenIssuerPort {
    String issue(AuthenticatedUser user);
    long expirationMinutes();
}
