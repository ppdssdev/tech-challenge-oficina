package br.com.fiap.techchallenge.oficina.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class StagingDatabaseIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("oficina_db").withUsername("bootstrap_admin").withPassword(UUID.randomUUID().toString());
    @TempDir Path directory;

    @Test void separateMigrationRuntimeAndLambdaRolesOverVerifiedTls() throws Exception {
        Path cert = directory.resolve("server.crt");
        Path key = directory.resolve("server.key");
        var openssl = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
            "-keyout", key.toString(), "-out", cert.toString(), "-days", "1", "-subj", "/CN=localhost",
            "-addext", "subjectAltName=DNS:localhost").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(openssl.waitFor()).isZero();
        postgres.copyFileToContainer(MountableFile.forHostPath(cert), "/var/lib/postgresql/data/server.crt");
        postgres.copyFileToContainer(MountableFile.forHostPath(key), "/var/lib/postgresql/data/server.key");
        assertThat(postgres.execInContainer("sh", "-c", "chown postgres:postgres /var/lib/postgresql/data/server.* && chmod 600 /var/lib/postgresql/data/server.key").getExitCode()).isZero();
        try (var admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = admin.createStatement()) {
            statement.execute("ALTER SYSTEM SET ssl = on");
            statement.execute("SELECT pg_reload_conf()");
            statement.execute(Files.readString(Path.of("scripts/staging/01-bootstrap-roles.sql")));
            for (String role : new String[]{"oficina_migrator", "oficina_api", "oficina_lambda"}) {
                statement.execute("ALTER ROLE " + role + " PASSWORD '" + postgres.getPassword() + "'");
            }
        }
        String url = "jdbc:postgresql://localhost:" + postgres.getMappedPort(5432)
            + "/oficina_db?sslmode=verify-full&sslrootcert=" + cert;
        Flyway.configure().dataSource(url, "oficina_migrator", postgres.getPassword()).load().migrate();
        try (var admin = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = admin.createStatement()) {
            statement.execute(Files.readString(Path.of("scripts/bootstrap-auth-reader.sql")));
            statement.execute(Files.readString(Path.of("scripts/staging/02-runtime-grants.sql")));
        }
        try (var api = DriverManager.getConnection(url, "oficina_api", postgres.getPassword()); var statement = api.createStatement()) {
            statement.execute("SELECT * FROM customers");
            statement.execute("SELECT * FROM work_order_status_history");
            try (var grants = statement.executeQuery("SELECT has_table_privilege(current_user, 'public.work_order_status_history', 'INSERT')")) {
                assertThat(grants.next()).isTrue();
                assertThat(grants.getBoolean(1)).isTrue();
            }
            statement.execute("INSERT INTO app_users VALUES (gen_random_uuid(), 'synthetic-admin', 'synthetic-hash', 'ADMIN', now(), now())");
            for (String sql : new String[]{"CREATE TABLE public.forbidden(id int)", "CREATE SCHEMA forbidden",
                "SELECT * FROM auth.customer_access", "UPDATE public.work_order_status_history SET event_order = 1", "SELECT * FROM flyway_schema_history", "SET ROLE oficina_migrator"}) {
                assertThatThrownBy(() -> statement.execute(sql)).as(sql).isInstanceOf(SQLException.class)
                    .extracting("SQLState").isEqualTo("42501");
            }
        }
        try (var lambda = DriverManager.getConnection(url, "oficina_lambda", postgres.getPassword()); var statement = lambda.createStatement()) {
            statement.execute("SELECT customer_id, active FROM auth.customer_identity_v2 WHERE cpf = 'synthetic' LIMIT 2");
            assertThatThrownBy(() -> statement.execute("SELECT * FROM customers")).isInstanceOf(SQLException.class)
                .extracting("SQLState").isEqualTo("42501");
        }
        assertThatThrownBy(() -> DriverManager.getConnection(url.replace("//localhost:", "//127.0.0.1:"),
            "oficina_api", postgres.getPassword())).isInstanceOf(SQLException.class);
        Path wrongCa = directory.resolve("wrong-ca.pem"); Files.writeString(wrongCa, "not a certificate");
        assertThatThrownBy(() -> DriverManager.getConnection(url.replace(cert.toString(), wrongCa.toString()),
            "oficina_api", postgres.getPassword())).isInstanceOf(SQLException.class);
    }
}
