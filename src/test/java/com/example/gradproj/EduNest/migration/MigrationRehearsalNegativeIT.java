package com.example.gradproj.EduNest.migration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Failure injection is confined to a newly created synthetic container. */
class MigrationRehearsalNegativeIT {
    @Test
    void rejectsUnsafeTargetsAndFailures() throws Exception {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);
        ((Logger) LoggerFactory.getLogger("tc")).setLevel(Level.ERROR);
        String id = UUID.randomUUID().toString();
        var db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName(MigrationRehearsalIT.SOURCE).withUsername("eb49")
                .withPassword(UUID.randomUUID().toString()).withLabel(MigrationRehearsalIT.LABEL, id);
        try {
            db.start();
            MigrationRehearsalIT.guardDatabase(db, MigrationRehearsalIT.SOURCE, id);
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.guardDatabase(db, "postgres", id));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.ownership(db, UUID.randomUUID().toString()));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.validateCredentials("", "x"));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.validateCredentials("eb49", null));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.exec(db, id, "missing tool", "sh", "-c", "command -v eb49_missing_tool >/dev/null"));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.exec(db, id, "missing backup", "pg_restore", "--list", "/tmp/eb49-missing.dump"));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.exec(db, id, "corrupted backup", "sh", "-c", "printf corrupt > /tmp/eb49-corrupt.dump; pg_restore --list /tmp/eb49-corrupt.dump"));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.exec(db, id, "failed restore", "sh", "-c", "PGPASSWORD=\"$POSTGRES_PASSWORD\" exec pg_restore --exit-on-error -U eb49 -d eb49_source /tmp/eb49-corrupt.dump"));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.compareFingerprints(Map.of("table_a", "before"), Map.of("table_a", "changed"), "Recovery"));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.requireCleanup(false));
            assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.migrate(db, MigrationRehearsalIT.SOURCE, id, "99", 0));
            MigrationRehearsalIT.migrate(db, MigrationRehearsalIT.SOURCE, id, "2", 2);
            try (var connection = MigrationRehearsalIT.connect(db, MigrationRehearsalIT.SOURCE, id); var statement = connection.createStatement()) {
                assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.validateMigrated(connection));
                var originalFingerprint = MigrationRehearsalIT.fingerprint(connection);
                statement.execute("alter table public.students drop constraint students_educational_level_check");
                statement.execute("alter table public.students add constraint students_educational_level_check check (educational_level in ('FRESH', 'JUNIOR', 'SENIOR', 'EXTRA'))");
                var modifiedFingerprint = MigrationRehearsalIT.fingerprint(connection);
                assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.compareFingerprints(originalFingerprint, modifiedFingerprint, "Recovery"));
                statement.execute("create table public.eb49_fingerprint_probe (value text, a boolean, b boolean, c boolean)");
                assertChecksDiffer(connection, "CHECK (value = 'a b')", "CHECK (value = 'ab')");
                assertChecksDiffer(connection, "CHECK (value = 'a(b)')", "CHECK (value = 'ab')");
                assertChecksDiffer(connection, "CHECK ((a OR b) AND c)", "CHECK (a OR (b AND c))");
                assertChecksDiffer(connection, "CHECK (value::varchar(1) = 'ab')", "CHECK (value::text = 'ab')");
                assertChecksDiffer(connection, "CHECK (value = 'left::text')", "CHECK (value = 'left')");
                statement.execute("drop table public.eb49_fingerprint_probe");
                // Failure injection only inside this disposable, owned database.
                statement.execute("alter table public.conversations drop column user1_id cascade");
            }
            assertThrows(Exception.class, () -> MigrationRehearsalIT.migrate(db, MigrationRehearsalIT.SOURCE, id, "3", 1));
        } finally {
            db.stop();
        }
        assertFalse(db.isRunning(), "Disposable failure-injection container must be removed");
    }

    private static void assertChecksDiffer(java.sql.Connection connection, String first, String second) throws Exception {
        String left = MigrationRehearsalIT.reparseCheck(connection, "eb49_fingerprint_probe", first);
        String right = MigrationRehearsalIT.reparseCheck(connection, "eb49_fingerprint_probe", second);
        assertNotEquals(left, right, "Distinct CHECK definitions must not collapse");
        assertThrows(IllegalStateException.class, () -> MigrationRehearsalIT.compareFingerprints(
                Map.of("constraint_probe", left), Map.of("constraint_probe", right), "Recovery"));
    }

    @Test
    void interruptionStillRunsCleanup() {
        boolean[] cleaned = {false};
        assertThrows(InterruptedException.class, () -> {
            try {
                throw new InterruptedException("simulated interruption");
            } finally {
                cleaned[0] = true;
            }
        });
        assertTrue(cleaned[0]);
    }
}
