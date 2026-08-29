package br.com.fiap.techchallenge.oficina.adapters.out.security;

import br.com.fiap.techchallenge.oficina.application.port.out.CredentialVerifierPort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CredentialVerifierSecurityAdapter implements CredentialVerifierPort {
    private final AuthenticationManager authenticationManager;

    public CredentialVerifierSecurityAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthenticatedUser verify(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        return new AuthenticatedUser(username);
    }
}
