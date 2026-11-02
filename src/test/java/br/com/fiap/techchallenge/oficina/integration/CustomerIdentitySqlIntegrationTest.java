package br.com.fiap.techchallenge.oficina.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CustomerIdentitySqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("identity_test")
        .withUsername("migration_owner")
        .withPassword(UUID.randomUUID().toString());
    static final String LOGIN_PASSWORD = UUID.randomUUID().toString();
    // Synthetic fixture generated from an arbitrary nine-digit seed, never personal input.
    static final String CPF = syntheticCpf("074839261");
    static final String LOOKUP =
        "SELECT customer_id, active FROM auth.customer_identity_v2 WHERE cpf = ? LIMIT 2";

    @BeforeAll
    static void migrateAndBootstrap() throws Exception {
        flyway(POSTGRES.getJdbcUrl(), null).migrate();
        try (var owner = owner(); var statement = owner.createStatement()) {
            statement.execute(bootstrap());
            statement.execute("CREATE ROLE lambda_test_login LOGIN INHERIT PASSWORD '" + LOGIN_PASSWORD + "'");
            statement.execute("GRANT oficina_auth_reader TO lambda_test_login");
        }
    }

    @BeforeEach
    void cleanCustomers() throws Exception {
        try (var connection = owner(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE public.customers CASCADE");
        }
    }

    @Test
    void exactLambdaQueryReturnsUuidAndExplicitBooleanEligibility() throws Exception {
        UUID id = insertCustomer(POSTGRES.getJdbcUrl(), "CPF", CPF);
        assertIdentity(CPF, id, false); // No access row: fail closed.
        setActive(id, true);
        assertIdentity(CPF, id, true);
        setActive(id, false);
        assertIdentity(CPF, id, false); // Lambda rejects active != true.
        try (var connection = owner(); var statement = connection.prepareStatement("DELETE FROM customers WHERE id = ?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
        assertAbsent(CPF);
        try (var connection = owner(); var result = connection.createStatement().executeQuery("SELECT count(*) FROM auth.customer_access")) {
            result.next();
            assertThat(result.getInt(1)).isZero();
        }
    }

    @Test
    void canonicalCpfIsUniqueAndAmbiguousLegacyDocumentsFailClosed() throws Exception {
        UUID id = insertCustomer(POSTGRES.getJdbcUrl(), "CPF", CPF);
        setActive(id, true);
        assertThatThrownBy(() -> insertCustomer(POSTGRES.getJdbcUrl(), "CPF", CPF))
            .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
        String formatted = CPF.substring(0, 3) + "." + CPF.substring(3, 6) + "."
            + CPF.substring(6, 9) + "-" + CPF.substring(9);
        insertCustomer(POSTGRES.getJdbcUrl(), "CPF", formatted);
        assertAbsent(CPF); // Even an enabled canonical row is hidden if another row normalizes to it.
        assertAbsent(formatted);
    }

    @Test
    void otherDocumentTypesMalformedUnknownAndSqlInjectionDoNotMatch() throws Exception {
        insertCustomer(POSTGRES.getJdbcUrl(), "CNPJ", CPF); // Legacy bad typing must not impersonate CPF.
        insertCustomer(POSTGRES.getJdbcUrl(), "CPF", "bad-document");
        assertAbsent(CPF);
        assertAbsent("bad-document");
        assertAbsent(syntheticCpf("093716482"));
        assertAbsent("' OR TRUE --");
    }

    @Test
    void realLoginCanOnlyReadIdentityViewAndCannotAssumeOwner() throws Exception {
        UUID id = insertCustomer(POSTGRES.getJdbcUrl(), "CPF", CPF);
        setActive(id, true);
        assertIdentity(CPF, id, true);
        try (var connection = reader(); var statement = connection.createStatement()) {
            // Deliberately do not use default_transaction_read_only: ACLs enforce the boundary.
            for (String sql : new String[]{
                "SELECT * FROM public.customers", "SELECT * FROM public.app_users",
                "SELECT * FROM public.work_orders", "SELECT * FROM auth.customer_access",
                "SELECT * FROM auth.customer_identity_v1",
                "UPDATE public.customers SET full_name = 'changed'",
                "INSERT INTO auth.customer_access(customer_id, active) VALUES (gen_random_uuid(), true)",
                "CREATE TABLE auth.forbidden(id int)", "CREATE TABLE public.forbidden(id int)",
                "CREATE SCHEMA forbidden", "DROP VIEW auth.customer_identity_v2",
                "SET ROLE migration_owner"
            }) {
                assertThatThrownBy(() -> statement.execute(sql)).as(sql)
                    .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
            }
            for (String sql : new String[]{
                "UPDATE auth.customer_identity_v2 SET active = false",
                "DELETE FROM auth.customer_identity_v2",
                "INSERT INTO auth.customer_identity_v2(customer_id, cpf, active) VALUES (gen_random_uuid(), 'x', true)"
            }) {
                assertThatThrownBy(() -> statement.execute(sql)).as(sql)
                    .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("55000");
            }
            try (var rows = statement.executeQuery("""
                SELECT has_table_privilege(current_user, 'auth.customer_identity_v2',
                    'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                """)) {
                rows.next();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            try (var rows = statement.executeQuery("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname IN ('public', 'auth') AND c.relkind IN ('r', 'v')
                  AND has_table_privilege(current_user, c.oid, 'SELECT')
                """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("customer_identity_v2");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void bootstrapRefusesPublicTableGrantsAndRollsBackRoleCreation() throws Exception {
        try (var connection = owner(); var statement = connection.createStatement()) {
            statement.execute("GRANT SELECT ON public.customers TO PUBLIC");
            try {
                assertThatThrownBy(() -> statement.execute(bootstrap().replace("oficina_auth_reader", "unsafe_reader_test")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Unsafe inherited privileges");
                statement.execute("ROLLBACK");
                try (var rows = statement.executeQuery("SELECT count(*) FROM pg_roles WHERE rolname = 'unsafe_reader_test'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            } finally {
                statement.execute("REVOKE SELECT ON public.customers FROM PUBLIC");
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"7", "10"})
    void upgradesExistingDatabaseWithoutRewritingCustomers(String version) throws Exception {
        String database = "upgrade_test_" + version;
        try (var connection = owner(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        String url = POSTGRES.getJdbcUrl().replace("/identity_test", "/" + database);
        flyway(url, version).migrate();
        UUID id = insertCustomer(url, "CPF", CPF);
        var migration = flyway(url, null);
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(11 - Integer.parseInt(version));
        migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement(LOOKUP)) {
            statement.setString(1, CPF);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject("customer_id", UUID.class)).isEqualTo(id);
                assertThat(result.getObject("active")).isEqualTo(false);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static UUID insertCustomer(String url, String type, String document) throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement("""
                 INSERT INTO public.customers(id, full_name, document_type, document_number, created_at, updated_at)
                 VALUES (?, 'Synthetic SQL fixture', ?, ?, now(), now())
                 """)) {
            statement.setObject(1, id);
            statement.setString(2, type);
            statement.setString(3, document);
            statement.executeUpdate();
        }
        return id;
    }

    private void setActive(UUID id, boolean active) throws Exception {
        try (var connection = owner(); var statement = connection.prepareStatement("""
            INSERT INTO auth.customer_access(customer_id, active) VALUES (?, ?)
            ON CONFLICT (customer_id) DO UPDATE SET active = excluded.active
            """)) {
            statement.setObject(1, id);
            statement.setBoolean(2, active);
            statement.executeUpdate();
        }
    }

    private void assertIdentity(String cpf, UUID id, boolean active) throws Exception {
        try (var connection = reader(); var statement = connection.prepareStatement(LOOKUP)) {
            statement.setString(1, cpf);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject("customer_id", UUID.class)).isEqualTo(id);
                assertThat(result.getObject("active")).isEqualTo(active);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private void assertAbsent(String cpf) throws Exception {
        try (var connection = reader(); var statement = connection.prepareStatement(LOOKUP)) {
            statement.setString(1, cpf);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection reader() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "lambda_test_login", LOGIN_PASSWORD);
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        if (target != null) config.target(target);
        return config.load();
    }

    private static String bootstrap() throws Exception {
        return Files.readString(Path.of("scripts/bootstrap-auth-reader.sql"));
    }

    private static String syntheticCpf(String seed) {
        String value = seed;
        for (int length = 9; length <= 10; length++) {
            int sum = 0;
            for (int i = 0; i < length; i++) sum += (value.charAt(i) - '0') * (length + 1 - i);
            int digit = (sum * 10) % 11;
            value += digit == 10 ? 0 : digit;
        }
        return value;
    }
}
