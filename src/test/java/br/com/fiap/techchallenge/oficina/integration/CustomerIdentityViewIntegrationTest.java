package br.com.fiap.techchallenge.oficina.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

class CustomerIdentityViewIntegrationTest {
    @Test
    void upgradesV9PreservingDataAndExposesOnlyCpfThroughLeastPrivilegeView() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("9").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                insert(connection, "52998224725", "CPF", true);
                insert(connection, "12345678909", "CPF", false);
                insert(connection, "529.982.247-25", "CPF", false);
                insert(connection, "12345678000195", "CNPJ", true);
                insert(connection, "invalid", "CPF", true);
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
                assertThat(count(connection, "select count(*) from public.customers")).isEqualTo(5);
                assertThat(count(connection, "select count(*) from auth.customer_identity_v1")).isEqualTo(3);
                assertThat(count(connection, "select count(*) from auth.customer_identity_v1 where cpf='52998224725'")).isEqualTo(2);
                assertThat(count(connection, "select count(*) from auth.customer_identity_v1 where cpf='12345678909' and not active")).isEqualTo(1);
                assertThat(count(connection, "select count(*) from auth.customer_identity_v1 where cpf='00000000000'")).isZero();
                assertThat(count(connection, "select count(*) from work_order_status_history")).isZero();
                try (var statement = connection.createStatement()) {
                    String grants = Files.readString(Path.of("scripts/provision-auth-readonly.sql"));
                    statement.execute(grants);
                    statement.execute(grants); // repeatable provisioning
                    statement.execute("set role auth_readonly");
                    assertThat(count(connection, "select count(*) from auth.customer_identity_v1 where active")).isEqualTo(1);
                    try (var result = statement.executeQuery("select customer_id, cpf, active from auth.customer_identity_v1 limit 1")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getObject(1)).isInstanceOf(UUID.class);
                        assertThat(result.getString(2)).matches("[0-9]{11}");
                        assertThat(result.getObject(3)).isInstanceOf(Boolean.class);
                    }
                    assertThatThrownBy(() -> statement.executeQuery("select * from public.customers"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("42501");
                    assertThatThrownBy(() -> statement.execute("update auth.customer_identity_v1 set active=true"))
                        .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> statement.execute("create table auth.forbidden(id int)"))
                        .isInstanceOf(SQLException.class);
                }
            }
        }
    }

    private static void insert(Connection connection, String document, String type, boolean active) throws SQLException {
        try (var statement = connection.prepareStatement("insert into customers (id, full_name, document_type, document_number, active, created_at, updated_at) values (?, 'Synthetic', ?, ?, ?, now(), now())")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, type);
            statement.setString(3, document);
            statement.setBoolean(4, active);
            statement.executeUpdate();
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
