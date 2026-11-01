package br.com.fiap.techchallenge.oficina.configuration;

import org.junit.jupiter.api.Test;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.BadJwtException;
import static org.assertj.core.api.Assertions.*;

class CustomerSecurityConfigTest {
    final CustomerSecurityConfig config = new CustomerSecurityConfig();
    @Test void cloudRequiresExplicitSecureTrust() {
        var env = new MockEnvironment();
        assertThatThrownBy(() -> config.customerDecoder(env, "", "", "", 60, 300, Ticker.systemTicker())).isInstanceOf(IllegalArgumentException.class);
        for (String uri : new String[]{"http://issuer.test/jwks", "http://localhost/jwks", "https://user:password@issuer.test/jwks", "https://issuer.test/jwks#fragment"})
            assertThatThrownBy(() -> config.customerDecoder(env, uri, "https://issuer.test", "oficina-api-test", 60, 300, Ticker.systemTicker())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.customerDecoder(env, "https://issuer.test/jwks", "http://issuer.test", "oficina-api-test", 60, 300, Ticker.systemTicker())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.customerDecoder(env, "https://issuer.test/jwks", "https://issuer.test", "oficina-api-test", 61, 300, Ticker.systemTicker())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.customerDecoder(env, "https://issuer.test/jwks", "https://issuer.test", "oficina-api-test", 60, 301, Ticker.systemTicker())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unconfiguredLocalFailsClosedAndProductionOverridesLocal() {
        var env = new MockEnvironment(); env.setActiveProfiles("local");
        var decoder = config.customerDecoder(env, "", "", "", 60, 300, Ticker.systemTicker());
        assertThatThrownBy(() -> decoder.decode("anything")).isInstanceOf(BadJwtException.class);
        env.setActiveProfiles("local", "production");
        assertThatThrownBy(() -> config.customerDecoder(env, "", "", "", 60, 300, Ticker.systemTicker())).isInstanceOf(IllegalArgumentException.class);
    }
}
