package br.com.fiap.techchallenge.oficina.configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class SecurityStartupValidator implements InitializingBean {
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("local", "test", "it");
    private static final Set<String> FORBIDDEN_SECRETS = Set.of(
        "awssecretpostech-oficina-fiap-2026",
        "local-only-jwt-secret-change-before-production-2026",
        "replace-with-a-random-secret-of-at-least-32-characters"
    );
    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of("admin", "admin123", "password",
        "replace-with-a-strong-admin-password");

    private final Environment environment;
    private final String jwtSecret;
    private final String adminPassword;

    public SecurityStartupValidator(
        Environment environment,
        @Value("${app.jwt.secret}") String jwtSecret,
        @Value("${app.admin.default-password}") String adminPassword
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
    }

    @Override
    public void afterPropertiesSet() {
        validate(environment.getActiveProfiles(), jwtSecret, adminPassword);
    }

    static void validate(String[] activeProfiles, String jwtSecret, String adminPassword) {
        boolean explicitlyNonProduction = Arrays.stream(activeProfiles).anyMatch(NON_PRODUCTION_PROFILES::contains);
        if (explicitlyNonProduction && Arrays.stream(activeProfiles).anyMatch(p -> !NON_PRODUCTION_PROFILES.contains(p))) {
            throw new IllegalStateException("Profiles não produtivos não podem ser combinados com profiles de outros ambientes.");
        }
        if (explicitlyNonProduction) {
            return;
        }
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32
            || predictable(jwtSecret) || FORBIDDEN_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                "APP_JWT_SECRET exige pelo menos 32 bytes UTF-8 e não pode usar valor demonstrativo ou previsível."
            );
        }
        if (adminPassword == null || adminPassword.length() < 16 || adminPassword.length() > 72
            || adminPassword.getBytes(StandardCharsets.UTF_8).length > 72
            || predictable(adminPassword) || FORBIDDEN_PASSWORDS.contains(adminPassword)) {
            throw new IllegalStateException(
                "APP_ADMIN_DEFAULT_PASSWORD exige 16 a 72 caracteres (máximo 72 bytes) e não pode ser previsível."
            );
        }
    }

    private static boolean predictable(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return value.isBlank() || value.chars().anyMatch(Character::isWhitespace)
            || value.chars().distinct().count() < 8
            || lower.matches(".*(replace|change.?me|placeholder|example|demo|local.only|test.secret|password|admin123|qwerty|0123456789|123456789|abcdefgh).*" )
            || value.matches("(.{1,16})\\1+");
    }
}
