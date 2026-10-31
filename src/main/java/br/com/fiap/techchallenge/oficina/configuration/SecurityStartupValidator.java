package br.com.fiap.techchallenge.oficina.configuration;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class SecurityStartupValidator implements InitializingBean {
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("local", "test", "it");
    private static final Set<String> FORBIDDEN_SECRETS = Set.of(
        "awssecretpostech-oficina-fiap-2026",
        "local-only-jwt-secret-change-before-production-2026"
    );
    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of("admin", "admin123", "password");

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
        if (explicitlyNonProduction) {
            return;
        }
        if (jwtSecret == null || jwtSecret.length() < 32 || FORBIDDEN_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                "APP_JWT_SECRET deve ser fornecido com pelo menos 32 caracteres e não pode usar valor demonstrativo."
            );
        }
        if (adminPassword == null || adminPassword.length() < 12 || FORBIDDEN_PASSWORDS.contains(adminPassword)) {
            throw new IllegalStateException(
                "APP_ADMIN_DEFAULT_PASSWORD deve ser fornecida com pelo menos 12 caracteres e não pode usar valor demonstrativo."
            );
        }
    }
}
