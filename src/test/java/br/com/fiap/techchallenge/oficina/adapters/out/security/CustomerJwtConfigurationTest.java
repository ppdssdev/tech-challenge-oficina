package br.com.fiap.techchallenge.oficina.adapters.out.security;

import br.com.fiap.techchallenge.oficina.configuration.CustomerJwtProperties;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static br.com.fiap.techchallenge.oficina.support.CustomerJwtFixtures.*;
import static org.assertj.core.api.Assertions.*;

class CustomerJwtConfigurationTest {
    @Test
    void disabledValidationNeverAuthenticates() throws Exception {
        var verifier = new CustomerJwtVerifier(new CustomerJwtProperties(false, null, null, null));
        String token = token(UUID.randomUUID().toString());
        assertThatThrownBy(() -> verifier.verify(token)).hasMessageContaining("disabled");
    }

    @Test
    void enabledValidationRequiresCompleteConfigurationAndStrongLocalPublicKeys() {
        for (var config : new CustomerJwtProperties[]{
            new CustomerJwtProperties(true, ISSUER, AUDIENCE, null),
            new CustomerJwtProperties(true, ISSUER, AUDIENCE, Map.of()),
            new CustomerJwtProperties(true, "", AUDIENCE, Map.of("current", CURRENT_FILE)),
            new CustomerJwtProperties(true, ISSUER, "", Map.of("current", CURRENT_FILE)),
            new CustomerJwtProperties(true, ISSUER, AUDIENCE, Map.of("../bad", CURRENT_FILE)),
            new CustomerJwtProperties(true, ISSUER, AUDIENCE, Map.of("current", "/nonexistent/key.pem")),
            new CustomerJwtProperties(true, ISSUER, AUDIENCE, Map.of("current", "https://untrusted.invalid/key.pem")),
            new CustomerJwtProperties(true, ISSUER, AUDIENCE, Map.of("current", publicFile(keyPair(1024))))
        }) {
            assertThatThrownBy(() -> new CustomerJwtVerifier(config)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
