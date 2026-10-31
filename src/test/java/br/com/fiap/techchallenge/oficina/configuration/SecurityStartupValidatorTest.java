package br.com.fiap.techchallenge.oficina.configuration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import static org.assertj.core.api.Assertions.*;

class SecurityStartupValidatorTest {
    // Public test fixtures, never deployment credentials.
    private static final String SECRET = "9f6bd278a1e045c389f7e2d160ab5c487acb0e23468fd197b83a95e26c04d751";
    private static final String PASSWORD = "vG7!pR2#nL9@cW4$zK6";

    @ParameterizedTest
    @ValueSource(strings = {"replace-with-a-random-secret-of-at-least-32-characters",
        "awssecretpostech-oficina-fiap-2026", "local-only-jwt-secret-change-before-production-2026",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "1234567890123456789012345678901234",
        "Ab1!Ab1!Ab1!Ab1!Ab1!Ab1!Ab1!Ab1!", ""})
    void rejectsUnsafeSecrets(String secret) {
        assertThatThrownBy(() -> SecurityStartupValidator.validate(new String[]{"prod"}, secret, PASSWORD))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_JWT_SECRET")
            .satisfies(error -> { if (!secret.isEmpty()) assertThat(error.getMessage()).doesNotContain(secret); });
    }

    @ParameterizedTest
    @ValueSource(strings = {"replace-with-a-strong-admin-password", "admin123", "passwordpasswordpassword",
        "aaaaaaaaaaaaaaaaaaaa", "Ab1!Ab1!Ab1!Ab1!", ""})
    void rejectsUnsafePasswords(String password) {
        assertThatThrownBy(() -> SecurityStartupValidator.validate(new String[]{"prod"}, SECRET, password))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_ADMIN_DEFAULT_PASSWORD")
            .satisfies(error -> { if (!password.isEmpty()) assertThat(error.getMessage()).doesNotContain(password); });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod,local", "prod,test", "prod,it", "production,local", "staging,local"})
    void rejectsMixedProfiles(String profiles) {
        assertThatThrownBy(() -> SecurityStartupValidator.validate(profiles.split(","), SECRET, PASSWORD))
            .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "it"})
    void allowsExplicitNonProduction(String profile) {
        assertThatCode(() -> SecurityStartupValidator.validate(new String[]{profile}, "local", "admin123"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", ""})
    void validatesStartupWithAndWithoutCredentials(String profile) {
        var runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SecurityStartupValidator.class)
            .withPropertyValues("spring.profiles.active=" + profile);
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("APP_JWT_SECRET=" + SECRET, "APP_ADMIN_DEFAULT_PASSWORD=" + PASSWORD)
            .run(context -> assertThat(context).hasNotFailed());
    }
}
