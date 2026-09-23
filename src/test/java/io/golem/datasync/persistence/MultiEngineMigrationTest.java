package io.golem.datasync.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class MultiEngineMigrationTest {
    @Test
    void backfillsExistingRunsAsZetaRuns() throws Exception {
        String url = "jdbc:h2:mem:migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        migrate(url, "1");

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            LocalDateTime now = LocalDateTime.now();
            try (var statement = connection.prepareStatement("""
                    INSERT INTO data_source_config
                    (id, name, type, host, port, database_name, username, encrypted_password, status, created_at, updated_at)
                    VALUES (?, ?, 'MYSQL', '127.0.0.1', 3306, 'demo', 'demo', 'encrypted', 'UNKNOWN', ?, ?)
                    """)) {
                for (String id : new String[] {"source", "target"}) {
                    statement.setString(1, id);
                    statement.setString(2, id);
                    statement.setObject(3, now);
                    statement.setObject(4, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO sync_job
                    (id, name, source_data_source_id, source_table, target_data_source_id, target_table,
                     write_mode, parallelism, batch_size, archived, created_at, updated_at)
                    VALUES ('job-1', 'job', 'source', 'source_table', 'target', 'target_table',
                            'APPEND', 1, 1000, FALSE, ?, ?)
                    """)) {
                statement.setObject(1, now);
                statement.setObject(2, now);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO sync_run
                    (id, job_id, seatunnel_job_id, status, created_at, updated_at)
                    VALUES ('run-1', 'job-1', 'zeta-job-1', 'RUNNING', ?, ?)
                    """)) {
                statement.setObject(1, now);
                statement.setObject(2, now);
                statement.executeUpdate();
            }
        }

        migrate(url, "2");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.prepareStatement("""
                     SELECT engine_type, engine_profile_id, external_job_id, metrics_available
                     FROM sync_run WHERE id = 'run-1'
                     """);
             var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("engine_type")).isEqualTo("ZETA");
            assertThat(result.getString("engine_profile_id")).isEqualTo("zeta-local");
            assertThat(result.getString("external_job_id")).isEqualTo("zeta-job-1");
            assertThat(result.getBoolean("metrics_available")).isTrue();
        }
    }

    private void migrate(String url, String target) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .target(MigrationVersion.fromVersion(target))
                .load()
                .migrate();
    }
}
