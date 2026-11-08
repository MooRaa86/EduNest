package com.example.gradproj.EduNest.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ArchiveRelocationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void missingSourceFailsAtomicallyThenRowsOwnerAndGrantsSurviveRelocation() throws Exception {
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("defaultCommissionRate", "10.0"));
        config.target("10").load().migrate();
        try (var conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO public.eb44_archive_conversations (id, created_at) VALUES (123, now())");
            stmt.execute("GRANT SELECT ON public.eb44_archive_conversations TO PUBLIC");
            String before;
            try (var rs = stmt.executeQuery("SELECT oid || ':' || relowner || ':' || relacl::text FROM pg_class WHERE oid = 'public.eb44_archive_conversations'::regclass")) {
                rs.next(); before = rs.getString(1);
            }
            stmt.execute("ALTER TABLE public.eb44_archive_messages RENAME TO temporarily_missing_archive_messages");
            var upgrade = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").placeholders(Map.of("defaultCommissionRate", "10.0")).load();
            assertThatThrownBy(upgrade::migrate).isInstanceOf(FlywayException.class)
                    .hasMessageContaining("expected ordinary table public.eb44_archive_messages");
            try (var rs = stmt.executeQuery("SELECT to_regclass('public.eb44_archive_conversations'), to_regclass('migration_archive.eb44_archive_conversations')")) {
                rs.next(); assertThat(rs.getString(1)).isNotNull(); assertThat(rs.getString(2)).isNull();
            }
            stmt.execute("ALTER TABLE public.temporarily_missing_archive_messages RENAME TO eb44_archive_messages");
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            try (var rs = stmt.executeQuery("SELECT oid || ':' || relowner || ':' || relacl::text FROM pg_class WHERE oid = 'migration_archive.eb44_archive_conversations'::regclass")) {
                rs.next(); assertThat(rs.getString(1)).isEqualTo(before);
            }
            try (var rs = stmt.executeQuery("SELECT count(*) FROM migration_archive.eb44_archive_conversations WHERE id = 123")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
