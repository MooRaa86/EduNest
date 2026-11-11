package com.example.gradproj.EduNest.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EB-51 Blocker 2: V9 Migration & Historical Attendance Idempotency Scenarios.
 * Tests migration behavior on disposable PostgreSQL 17 across 5 critical dimensions:
 * 1. Clean database (no duplicates).
 * 2. Duplicate identical records (verifies fail-closed deployment blocker).
 * 3. Duplicate records with conflicting attendance values (verifies historical data preservation).
 * 4. Existing points consistency in TotalPoints.
 * 5. FK dependency audit on session_attendance_result.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("EB-51 Blocker 2: PostgreSQL V9 Migration Scenarios Test")
public class PostgreSqlV9MigrationScenariosTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private String createDisposableDatabase() throws Exception {
        String dbName = "v9_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection rootConn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = rootConn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        }
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + dbName);
    }

    private FluentConfiguration flywayConfig(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .outOfOrder(true)
                .placeholders(Map.of("defaultCommissionRate", "10.0"));
    }

    private void seedBasicPrerequisites(Connection conn, long studentId, long mentorId, long mentorshipId, long weekId, long sessionId) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO roles (id, name) VALUES (1, 'ROLE_STUDENT'), (2, 'ROLE_MENTOR') ON CONFLICT DO NOTHING");
            stmt.execute("INSERT INTO users (id, deleted, enabled, role_id, email, password, first_name, last_name, phone_number, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (" + studentId + ", false, true, 1, 'student-" + studentId + "@test.org', 'pwd', 'Student', 'User', '1234567890', gen_random_uuid(), now(), now(), 'test', 'test')");
            stmt.execute("INSERT INTO students (student_id, educational_level) VALUES (" + studentId + ", 'FRESH')");

            stmt.execute("INSERT INTO users (id, deleted, enabled, role_id, email, password, first_name, last_name, phone_number, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (" + mentorId + ", false, true, 2, 'mentor-" + mentorId + "@test.org', 'pwd', 'Mentor', 'User', '1234567891', gen_random_uuid(), now(), now(), 'test', 'test')");
            stmt.execute("INSERT INTO mentors (mentor_id) VALUES (" + mentorId + ")");

            stmt.execute("INSERT INTO mentorship (id, mentor_id, title, description, category, duration, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (" + mentorshipId + ", " + mentorId + ", 'Mentorship " + mentorshipId + "', 'Desc', 'BACKEND', 10, gen_random_uuid(), now(), now(), 'test', 'test')");
            stmt.execute("INSERT INTO weeks (id, mentorship_id, title, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (" + weekId + ", " + mentorshipId + ", 'Week 1', gen_random_uuid(), now(), now(), 'test', 'test')");
            stmt.execute("INSERT INTO sessions (id, week_id, title, status, scheduled_at, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (" + sessionId + ", " + weekId + ", 'Session " + sessionId + "', 'ENDED', now(), gen_random_uuid(), now(), now(), 'test', 'test')");
        }
    }

    @Test
    @DisplayName("Scenario 1: Clean database without duplicates applies V9 successfully and creates unique index")
    void cleanDatabaseAppliesV9Successfully() throws Exception {
        String jdbcUrl = createDisposableDatabase();

        Flyway flyway = flywayConfig(jdbcUrl)
                .target("10")
                .load();

        int migrationsApplied = flyway.migrate().migrationsExecuted;
        assertThat(migrationsApplied).isEqualTo(10);

        // Verify unique index exists in PostgreSQL
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM pg_indexes WHERE indexname = 'uk_session_attendance_result_session_student'"
            );
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @DisplayName("Scenario 2: Database with duplicate records halts V9 migration with deployment blocker exception")
    void duplicateRecordsBlockV9MigrationFailClosed() throws Exception {
        String jdbcUrl = createDisposableDatabase();

        // 1. Migrate up to V9
        Flyway flywayV8 = flywayConfig(jdbcUrl)
                .target("9")
                .load();
        flywayV8.migrate();

        // 2. Insert prerequisite rows and duplicate attendance results
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            seedBasicPrerequisites(conn, 101L, 102L, 201L, 301L, 401L);

            // Insert duplicate records for the same (session_id=401, student_id=101)
            stmt.execute("INSERT INTO session_attendance_result (id, session_id, student_id, attended, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (1, 401, 101, true, gen_random_uuid(), now(), now(), 'test', 'test')");
            stmt.execute("INSERT INTO session_attendance_result (id, session_id, student_id, attended, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (2, 401, 101, true, gen_random_uuid(), now(), now(), 'test', 'test')");
        }

        // 3. Attempt V9 migration: must FAIL-CLOSED without silently deleting data
        Flyway flywayV9 = flywayConfig(jdbcUrl)
                .target("10")
                .load();

        assertThatThrownBy(flywayV9::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("EB-51 V9 deployment blocked")
                .hasMessageContaining("duplicate (session_id, student_id) pairs");

        // 4. Verify historical data was NOT silently deleted
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM session_attendance_result WHERE session_id = 401 AND student_id = 101");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Scenario 3: Conflicting attendance values (attended=true vs false) are preserved and migration blocked")
    void conflictingAttendanceValuesHaltMigrationWithoutDiscardingData() throws Exception {
        String jdbcUrl = createDisposableDatabase();

        Flyway flywayV8 = flywayConfig(jdbcUrl)
                .target("9")
                .load();
        flywayV8.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            seedBasicPrerequisites(conn, 201L, 202L, 301L, 401L, 501L);

            // Row 1: attended = false
            stmt.execute("INSERT INTO session_attendance_result (id, session_id, student_id, attended, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (10, 501, 201, false, gen_random_uuid(), now(), now(), 'test', 'test')");
            // Row 2: attended = true (conflicting evaluation!)
            stmt.execute("INSERT INTO session_attendance_result (id, session_id, student_id, attended, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (11, 501, 201, true, gen_random_uuid(), now(), now(), 'test', 'test')");
        }

        Flyway flywayV9 = flywayConfig(jdbcUrl)
                .target("10")
                .load();

        assertThatThrownBy(flywayV9::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("EB-51 V9 deployment blocked");

        // Both rows must still exist exactly as before
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT attended FROM session_attendance_result WHERE session_id = 501 AND student_id = 201 ORDER BY id");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isTrue();
        }
    }

    @Test
    @DisplayName("Scenario 4: Existing TotalPoints balances remain unmutated by V9 migration check")
    void existingTotalPointsBalancesPreserved() throws Exception {
        String jdbcUrl = createDisposableDatabase();

        Flyway flywayV8 = flywayConfig(jdbcUrl)
                .target("9")
                .load();
        flywayV8.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            seedBasicPrerequisites(conn, 301L, 302L, 401L, 501L, 601L);

            stmt.execute("INSERT INTO total_points (id, student_id, mentorship_id, total_points, version, public_id, created_at, updated_at, created_by, updated_by) " +
                    "VALUES (1, 301, 401, 75, 0, gen_random_uuid(), now(), now(), 'test', 'test')");
        }

        Flyway flywayV9 = flywayConfig(jdbcUrl)
                .target("10")
                .load();
        flywayV9.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT total_points, version FROM total_points WHERE student_id = 301 AND mentorship_id = 401");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("total_points")).isEqualTo(75);
            assertThat(rs.getLong("version")).isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("Scenario 5: FK dependency audit confirms no tables reference session_attendance_result(id)")
    void fkDependencyAuditConfirmsNoForeignKeysToSessionAttendanceResult() throws Exception {
        String jdbcUrl = createDisposableDatabase();

        Flyway flyway = flywayConfig(jdbcUrl)
                .target("10")
                .load();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT tc.table_name, kcu.column_name, ccu.table_name AS foreign_table_name " +
                            "FROM information_schema.table_constraints AS tc " +
                            "JOIN information_schema.key_column_usage AS kcu ON tc.constraint_name = kcu.constraint_name " +
                            "JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name " +
                            "WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'session_attendance_result'"
            );
            // No tables reference session_attendance_result as a foreign table
            assertThat(rs.next()).isFalse();
        }
    }
}
