package br.com.fiap.techchallenge.oficina.configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("staging")
public class StagingConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateStagingConfiguration(Environment environment) {
        validate(environment);
        return factory -> { };
    }

    public static void validate(Environment env) {
        if (Set.of(env.getActiveProfiles()).contains("local")) fail("Do not combine staging and local profiles.");
        validateDatabaseUrl(required(env.getProperty("spring.datasource.url")));
        if (!"oficina_api".equals(required(env.getProperty("spring.datasource.username")))) fail("Runtime must use oficina_api.");
        required(env.getProperty("spring.datasource.password"));
        String secret = required(env.getProperty("app.jwt.secret"));
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || secret.equals("awssecretpostech-oficina-fiap-2026")) {
            fail("A dedicated HMAC secret of at least 32 bytes is required.");
        }
        required(env.getProperty("app.admin.default-username"));
        String password = required(env.getProperty("app.admin.default-password"));
        if (password.length() < 16 || password.equals("admin123")) fail("A dedicated administrative password is required.");
        if (!"false".equals(env.getProperty("spring.flyway.enabled"))
            || !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))) fail("Runtime DDL is forbidden.");
    }

    public static String required(String value) {
        if (value == null || value.isBlank() || value.contains("${")) fail("Missing staging configuration.");
        return value;
    }

    public static void validateDatabaseUrl(String value) {
        try {
            required(value);
            if (!value.startsWith("jdbc:postgresql://")) fail("PostgreSQL JDBC URL required.");
            URI uri = URI.create(value.substring(5));
            String host = uri.getHost();
            if (host == null || !host.matches("[a-zA-Z0-9.-]+\\.rds\\.amazonaws\\.com(\\.cn)?")
                || uri.getUserInfo() != null || uri.getFragment() != null
                || !"/oficina_db".equals(uri.getPath()) || (uri.getPort() != -1 && uri.getPort() != 5432)) {
                fail("Use the real RDS hostname and oficina_db database.");
            }
            var parameters = new HashMap<String, String>();
            for (String entry : required(uri.getRawQuery()).split("&")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2) fail("Invalid JDBC parameter.");
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String parameter = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (!Set.of("sslmode", "sslrootcert", "connectTimeout", "socketTimeout").contains(key)
                    || parameters.putIfAbsent(key, parameter) != null) fail("Unsupported or duplicate JDBC parameter.");
            }
            if (!"verify-full".equals(parameters.get("sslmode"))) fail("TLS verify-full is required.");
            Path ca = Path.of(required(parameters.get("sslrootcert")));
            if (!ca.isAbsolute() || !Files.isRegularFile(ca) || !Files.isReadable(ca)) fail("Readable CA bundle required.");
            try (var input = Files.newInputStream(ca)) {
                if (java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(input).isEmpty()) {
                    fail("Empty CA bundle.");
                }
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid staging PostgreSQL TLS configuration.", exception);
        }
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
