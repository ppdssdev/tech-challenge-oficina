package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.AuthenticateUserUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.CredentialVerifierPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TokenIssuerPort;

public final class AuthenticateUserService implements AuthenticateUserUseCase {
    private final CredentialVerifierPort credentials;
    private final TokenIssuerPort tokens;

    public AuthenticateUserService(CredentialVerifierPort credentials, TokenIssuerPort tokens) {
        this.credentials = credentials;
        this.tokens = tokens;
    }

    @Override
    public Token authenticate(String username, String password) {
        var user = credentials.verify(username, password);
        return new Token("Bearer", tokens.issue(user), tokens.expirationMinutes());
    }
}
