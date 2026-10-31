package br.com.fiap.techchallenge.oficina.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityStartupValidatorTest {

    @Test
    void shouldRejectDemonstrationCredentialsWithoutExplicitNonProductionProfile() {
        assertThatThrownBy(() -> SecurityStartupValidator.validate(
            new String[] {"prod"}, "awssecretpostech-oficina-fiap-2026", "admin123"
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_JWT_SECRET");
    }

    @Test
    void shouldRejectWeakAdminPasswordOutsideNonProductionProfiles() {
        assertThatThrownBy(() -> SecurityStartupValidator.validate(
            new String[] {"prod"}, "a-secure-random-jwt-secret-with-more-than-32-characters", "admin123"
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_ADMIN_DEFAULT_PASSWORD");
    }

    @Test
    void shouldAcceptStrongExternalCredentialsAndExplicitLocalProfile() {
        assertThatCode(() -> SecurityStartupValidator.validate(
            new String[] {"prod"}, "a-secure-random-jwt-secret-with-more-than-32-characters", "strong-admin-password"
        )).doesNotThrowAnyException();
        assertThatCode(() -> SecurityStartupValidator.validate(
            new String[] {"local"}, "local-only-jwt-secret-change-before-production-2026", "admin123"
        )).doesNotThrowAnyException();
    }
}
