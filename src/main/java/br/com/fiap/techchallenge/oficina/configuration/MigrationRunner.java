package br.com.fiap.techchallenge.oficina.configuration;

import org.flywaydb.core.Flyway;

/** Dedicated Job entry point: no Spring context, web server, JPA, admin seed or scheduler. */
public final class MigrationRunner {
    public static void main(String[] args) {
        String url = StagingConfiguration.required(System.getenv("MIGRATION_JDBC_URL"));
        StagingConfiguration.validateDatabaseUrl(url);
        String user = StagingConfiguration.required(System.getenv("MIGRATION_USERNAME"));
        if (!"oficina_migrator".equals(user)) throw new IllegalArgumentException("Migration role required.");
        String password = StagingConfiguration.required(System.getenv("MIGRATION_PASSWORD"));
        Flyway.configure().dataSource(url, user, password).defaultSchema("public")
            .cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true).load().migrate();
    }
    private MigrationRunner() { }
}
