package br.com.fiap.techchallenge.oficina.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class StagingConfigurationTest {
    @TempDir static Path directory;
    static String url;
    @BeforeAll static void certificate() throws Exception {
        var process = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
            "-keyout", directory.resolve("key.pem").toString(), "-out", directory.resolve("ca.pem").toString(),
            "-days", "1", "-subj", "/CN=Synthetic CA").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor()).isZero();
        url = "jdbc:postgresql://synthetic.region.rds.amazonaws.com:5432/oficina_db?sslmode=verify-full&sslrootcert="
            + directory.resolve("ca.pem");
    }
    private MockEnvironment valid() {
        var env = new MockEnvironment();
        env.setActiveProfiles("staging");
        return env.withProperty("spring.datasource.url", url).withProperty("spring.datasource.username", "oficina_api")
            .withProperty("spring.datasource.password", java.util.UUID.randomUUID().toString())
            .withProperty("app.jwt.secret", java.util.UUID.randomUUID().toString())
            .withProperty("app.admin.default-username", "synthetic-admin")
            .withProperty("app.admin.default-password", java.util.UUID.randomUUID().toString())
            .withProperty("spring.flyway.enabled", "false").withProperty("spring.jpa.hibernate.ddl-auto", "validate");
    }
    @Test void acceptsStrictConfiguration() { assertThatCode(() -> StagingConfiguration.validate(valid())).doesNotThrowAnyException(); }
    @Test void rejectsMissingSecretsAndRuntimeDdl() {
        for (String key : new String[]{"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "app.jwt.secret", "app.admin.default-username", "app.admin.default-password"}) {
            var env = valid().withProperty(key, "");
            assertThatThrownBy(() -> StagingConfiguration.validate(env)).as(key).isInstanceOf(IllegalArgumentException.class);
        }
        for (var entry : java.util.Map.of("spring.flyway.enabled", "true", "spring.jpa.hibernate.ddl-auto", "update",
            "spring.datasource.username", "oficina_admin", "app.admin.default-password", "admin123",
            "app.jwt.secret", "awssecretpostech-oficina-fiap-2026").entrySet()) {
            assertThatThrownBy(() -> StagingConfiguration.validate(valid().withProperty(entry.getKey(), entry.getValue())))
                .isInstanceOf(IllegalArgumentException.class);
        }
        var mixed = valid(); mixed.setActiveProfiles("staging", "local");
        assertThatThrownBy(() -> StagingConfiguration.validate(mixed)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsUnverifiedTlsWrongDatabaseAndOverrideParameters() {
        for (String invalid : new String[]{url.replace("verify-full", "require"), url.replace("verify-full", "disable"),
            url.replace("oficina_db", "oficina"), url.replace("synthetic.region.rds.amazonaws.com", "127.0.0.1"),
            url + "&sslmode=disable", url + "&sslfactory=org.postgresql.ssl.NonValidatingFactory",
            url.replace("ca.pem", "missing.pem"), url.replace("ca.pem", "key.pem")}) {
            assertThatThrownBy(() -> StagingConfiguration.validateDatabaseUrl(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void manifestsKeepLocalAndStagingSeparate() throws Exception {
        String api = Files.readString(Path.of("k8s/staging/api.yaml"));
        assertThat(api).doesNotContain("initContainers", "oficina-postgres", "kind: Secret", "MIGRATION_PASSWORD")
            .contains("secretKeyRef", "readinessProbe", "livenessProbe", "resources:", "value: staging");
        assertThat(Files.readString(Path.of("k8s/staging/kustomization.yaml"))).doesNotContain("migration-job.yaml", "postgres.yaml");
        assertThat(Files.readString(Path.of("k8s/local/kustomization.yaml"))).contains("postgres.yaml");
        assertThat(Files.readString(Path.of("src/main/resources/application-staging.yml")))
            .contains("enabled: false", "ddl-auto: validate").doesNotContain("admin123", "awssecretpostech");
    }
}
