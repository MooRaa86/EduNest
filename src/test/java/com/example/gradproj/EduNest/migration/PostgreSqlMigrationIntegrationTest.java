package com.example.gradproj.EduNest.migration;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.UUID;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * EB-43: Real PostgreSQL Integration Test Suite.
 * Covers all 10 migration lifecycle and production database hygiene requirements against PostgreSQL 17.
 * Uses Testcontainers with graceful skip when Docker is unavailable in the environment.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("EB-43: PostgreSQL Migration Lifecycle & Production Hygiene Integration Tests")
public class PostgreSqlMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static Flyway primaryFlyway;

    @BeforeAll
    static void checkDockerAndInit() {
        try {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Docker daemon is not available; skipping Testcontainers PostgreSQL integration suite");
        } catch (Exception e) {
            Assumptions.abort("Docker check threw exception; skipping Testcontainers suite: " + e.getMessage());
        }

        if (postgres.isRunning()) {
            primaryFlyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .placeholders(Map.of("defaultCommissionRate", "10.0"))
                    .load();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Fresh database migration: empty schema migrates to latest version")
    void testFreshDatabaseMigration() {
        assertThat(primaryFlyway).isNotNull();

        MigrateResult result = primaryFlyway.migrate();
        assertThat(result.success)
                .as("Fresh migration against empty schema must succeed")
                .isTrue();
        assertThat(result.migrationsExecuted)
                .as("Initial migration must execute exactly 4 scripts (V1, V2, V3, V4 — EB-47/P3-05 commission seed)")
                .isEqualTo(4);
    }

    @Test
    @Order(2)
    @DisplayName("2. Migration history: applied migrations (V1, V2, V3, V4, V6), SUCCESS, non-null checksums")
    void testMigrationHistoryVerification() {
        MigrationInfo[] applied = primaryFlyway.info().applied();
        assertThat(applied)
                .as("Exactly 4 migrations must be registered in flyway_schema_history")
                .hasSize(5);

        MigrationInfo v1 = applied[0];
        assertThat(v1.getVersion().getVersion()).isEqualTo("1");
        assertThat(v1.getDescription()).isEqualTo("initial schema");
        assertThat(v1.getType().name()).isEqualTo("SQL");
        assertThat(v1.getState().isApplied()).isTrue();
        assertThat(v1.getChecksum()).isNotNull();

        MigrationInfo v2 = applied[1];
        assertThat(v2.getVersion().getVersion()).isEqualTo("2");
        assertThat(v2.getDescription()).isEqualTo("mentorship explore trigram index");
        assertThat(v2.getType().name()).isEqualTo("SQL");
        assertThat(v2.getState().isApplied()).isTrue();
        assertThat(v2.getChecksum()).isNotNull();

        // [EB-44][P3-02] V3 baseline commitment
        MigrationInfo v3 = applied[2];
        assertThat(v3.getVersion().getVersion()).isEqualTo("3");
        assertThat(v3.getDescription()).isEqualTo("uniqueness constraints and dedupe");
        assertThat(v3.getType().name()).isEqualTo("SQL");
        assertThat(v3.getState().isApplied()).isTrue();
        assertThat(v3.getChecksum()).isNotNull();

        // [EB-47][P3-05] V4 commission default seeding
        MigrationInfo v4 = applied[3];
        assertThat(v4.getVersion().getVersion()).isEqualTo("4");
        assertThat(v4.getDescription()).isEqualTo("seed default commission");
        assertThat(v4.getType().name()).isEqualTo("SQL");
        assertThat(v4.getState().isApplied()).isTrue();
        assertThat(v4.getChecksum()).isNotNull();

        // [EB-109] V6 public uuid identifiers (V5 reserved for EB-91)
        MigrationInfo v6 = applied[4];
        assertThat(v6.getVersion().getVersion()).isEqualTo("6");
        assertThat(v6.getDescription()).isEqualTo("add public uuid identifiers");
        assertThat(v6.getType().name()).isEqualTo("SQL");
        assertThat(v6.getState().isApplied()).isTrue();
        assertThat(v6.getChecksum()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("3. Schema verification: 46 tables (42 entity + 4 EB-44 archive), 5 TEXT columns, trgm extension & indexes, seeded roles")
    void testSchemaVerification() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            // A. Verify exactly 46 application tables in public schema
            // [EB-44][P3-02] 42 EB-82-reconciled entity tables + 4 one-time
            // dedupe archive surfaces (eb44_archive_*), flipped baseline
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_type = 'BASE TABLE'
                       AND table_name != 'flyway_schema_history'
                     ORDER BY table_name
                 """)) {
                List<String> tables = new ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
                assertThat(tables)
                        .as("Schema must contain exactly 46 application tables: 42 entity tables matching EB-82 reconciliation + 4 EB-44 archive tables")
                        .hasSize(46);
                assertThat(tables)
                        .as("All four EB-44 archive surfaces must exist")
                        .contains("eb44_archive_enrollments", "eb44_archive_mentorship_reviews",
                                "eb44_archive_conversations", "eb44_archive_messages");
            }

            // B. Verify all 5 text columns are data_type = 'text' (no OID or bytea)
            Map<String, String> textColumns = Map.of(
                    "contact_message", "message",
                    "projects", "project_brief",
                    "project_submission", "project_feedback",
                    "tasks", "task_description",
                    "task_submission", "task_feedback"
            );

            for (Map.Entry<String, String> entry : textColumns.entrySet()) {
                String table = entry.getKey();
                String column = entry.getValue();
                try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
                    ps.setString(1, table);
                    ps.setString(2, column);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("Column " + table + "." + column + " must exist in database")
                                .isTrue();
                        String dataType = rs.getString("data_type");
                        assertThat(dataType)
                                .as(table + "." + column + " data_type must be 'text' (not oid or bytea)")
                                .isEqualTo("text");
                    }
                }
            }

            // C. Verify pg_trgm extension and indexes exist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'pg_trgm'")) {
                assertThat(rs.next())
                        .as("pg_trgm extension must be installed in PostgreSQL by V2")
                        .isTrue();
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT indexname FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND indexname IN ('idx_mentorship_explore_trgm', 'idx_mentorship_active_price', 'idx_users_mentor_name_trgm')
                 """)) {
                List<String> indexes = new ArrayList<>();
                while (rs.next()) {
                    indexes.add(rs.getString("indexname"));
                }
                assertThat(indexes)
                        .as("All 3 performance and trigram indexes defined in V2 must exist")
                        .contains("idx_mentorship_explore_trgm", "idx_mentorship_active_price", "idx_users_mentor_name_trgm");
            }

            // C2. [EB-44][P3-02] uniqueness DDL committed + redundant indexes retired
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT indexname FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND indexname IN ('uk_enrollment_student_mentorship', 'uk_mentorship_review_student',
                                         'uk_conversation_unordered_pair', 'idx_enrollments_mentorship',
                                         'idx_enrollments_student_mentorship', 'idx_mentorship_student')
                 """)) {
                List<String> indexes44 = new ArrayList<>();
                while (rs.next()) {
                    indexes44.add(rs.getString("indexname"));
                }
                assertThat(indexes44)
                        .as("All EB-44 uniqueness/covering indexes must exist (P3-02)")
                        .contains("uk_enrollment_student_mentorship", "uk_mentorship_review_student",
                                "uk_conversation_unordered_pair", "idx_enrollments_mentorship");
                assertThat(indexes44)
                        .as("Redundant pair indexes retired by V3 must be gone")
                        .doesNotContain("idx_enrollments_student_mentorship", "idx_mentorship_student");
            }

            // D. Verify initial reference roles seeded by V1
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM roles ORDER BY name")) {
                List<String> roles = new ArrayList<>();
                while (rs.next()) {
                    roles.add(rs.getString("name"));
                }
                assertThat(roles)
                        .as("Initial reference roles (ADMIN, MENTOR, STUDENT) must be seeded idempotently")
                        .contains("ADMIN", "MENTOR", "STUDENT");
            }

            // E. [EB-47][P3-05] Verify default commission seeded at 10.0
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT rate, created_by, updated_by FROM commission")) {
                assertThat(rs.next())
                        .as("Default commission record must be seeded on fresh deployment")
                        .isTrue();
                assertThat(rs.getDouble("rate")).isEqualTo(10.0);
                assertThat(rs.getString("created_by")).isEqualTo("SYSTEM");
                assertThat(rs.getString("updated_by")).isEqualTo("SYSTEM");
                assertThat(rs.next())
                        .as("Exactly one default commission row should be seeded initially")
                        .isFalse();
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. Hibernate validation: EntityManagerFactory boots with ddl-auto=validate without exception")
    void testHibernateValidationAgainstMigratedSchema() {
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        emfBean.setPackagesToScan("com.example.gradproj.EduNest.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        emfBean.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> jpaProperties = new HashMap<>();
        jpaProperties.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        jpaProperties.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        jpaProperties.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        jpaProperties.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.hbm2ddl.auto", "validate"); // STRICT VALIDATE MODE
        jpaProperties.put("hibernate.show_sql", "false");
        jpaProperties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        jpaProperties.put("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");

        emfBean.setJpaPropertyMap(jpaProperties);

        assertDoesNotThrow(() -> {
            emfBean.afterPropertiesSet();
            EntityManagerFactory emf = emfBean.getObject();
            assertThat(emf).isNotNull();
            assertThat(emf.isOpen()).isTrue();
            emf.close();
        }, "JPA EntityManagerFactory with ddl-auto=validate must succeed without SchemaManagementException");
    }

    @Test
    @Order(5)
    @DisplayName("5. Migration idempotency: running migrate again results in 0 migrations executed")
    void testMigrationIdempotency() {
        MigrateResult repeatResult = primaryFlyway.migrate();
        assertThat(repeatResult.success).isTrue();
        assertThat(repeatResult.migrationsExecuted)
                .as("Subsequent migrate() invocation must be idempotent and execute 0 migrations")
                .isEqualTo(0);
    }

    @Test
    @Order(6)
    @DisplayName("6. Migration immutability: tampering with applied checksum causes Flyway validation failure")
    void testMigrationImmutabilityTampering() throws Exception {
        String testSchema = "immutability_test";
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + testSchema);
        }

        try {
            Flyway isolatedFlyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(testSchema)
                    .defaultSchema(testSchema)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .placeholders(Map.of("defaultCommissionRate", "10.0"))
                    .load();

            isolatedFlyway.migrate();

            // Tamper with checksum of V1 in this isolated schema
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE " + testSchema + ".flyway_schema_history SET checksum = 99999999 WHERE version = '1'");
            }

            // Flyway validation must detect checksum mutation and reject it
            assertThatThrownBy(isolatedFlyway::validate)
                    .as("Flyway validate() must fail when applied checksum does not match classpath script")
                    .isInstanceOf(FlywayException.class);
        } finally {
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("7. Partial migration failure recovery: PostgreSQL transactional DDL rolls back cleanly on error")
    void testTransactionalRollbackOnFailure() throws Exception {
        String testSchema = "rollback_test";
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + testSchema);

            // Execute transaction with valid DDL followed by deliberate runtime failure (division by zero)
            conn.setAutoCommit(false);
            try (Statement txStmt = conn.createStatement()) {
                txStmt.execute("CREATE TABLE " + testSchema + ".temp_rollback_table (id INT PRIMARY KEY);");
                // Intentionally fail
                txStmt.execute("SELECT 1 / 0;");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }

            // Verify that the table was cleanly rolled back and does not exist
            try (Statement checkStmt = conn.createStatement();
                 ResultSet rs = checkStmt.executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'rollback_test' AND table_name = 'temp_rollback_table'
                 """)) {
                assertThat(rs.next())
                        .as("Table created in failed transaction must have been rolled back completely")
                        .isFalse();
            }
        } finally {
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. Existing unmanaged database behavior: baseline-on-migrate=false rejects unmanaged schema, manual baseline succeeds")
    void testExistingUnmanagedDatabaseBehavior() throws Exception {
        String testSchema = "unmanaged_test";
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + testSchema);
            stmt.execute("CREATE TABLE " + testSchema + ".unmanaged_table (id INT PRIMARY KEY);");
        }

        try {
            Flyway unmanagedFlyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(testSchema)
                    .defaultSchema(testSchema)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false) // Must be false
                    .baselineVersion("1")
                    .cleanDisabled(true)
                    .placeholders(Map.of("defaultCommissionRate", "10.0"))
                    .load();

            // 1. With baselineOnMigrate=false, migrate() must refuse to run against non-empty unmanaged schema
            assertThatThrownBy(unmanagedFlyway::migrate)
                    .as("baseline-on-migrate=false must refuse to run against populated unmanaged database")
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("non-empty schema");

            // 2. Explicit manual baseline establishes baseline version 1
            unmanagedFlyway.baseline();

            assertThat(unmanagedFlyway.info().current().getVersion().getVersion())
                    .as("Manual baseline must record version 1 in flyway_schema_history")
                    .isEqualTo("1");

            // 3. Subsequent migrate applies pending migrations above baseline (V2, V3)
            MigrateResult postBaselineResult = unmanagedFlyway.migrate();
            assertThat(postBaselineResult.success).isTrue();
            assertThat(postBaselineResult.migrationsExecuted)
                    .as("Post-baseline migrate must apply pending migrations above version 1 (V2, V3, V4 — EB-47/P3-05)")
                    .isEqualTo(3);
        } finally {
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("9. Show-sql disabled verification: production and base properties enforce show-sql=false")
    void testShowSqlDisabledVerification() throws IOException {
        Resource prodResource = new ClassPathResource("application-prod.properties");
        Properties prodProps = PropertiesLoaderUtils.loadProperties(prodResource);
        assertThat(prodProps.getProperty("spring.jpa.show-sql"))
                .as("application-prod.properties must enforce spring.jpa.show-sql=false")
                .isEqualTo("false");

        Resource baseResource = new ClassPathResource("application.properties");
        Properties baseProps = PropertiesLoaderUtils.loadProperties(baseResource);
        assertThat(baseProps.getProperty("spring.jpa.show-sql"))
                .as("application.properties must enforce spring.jpa.show-sql=false")
                .isEqualTo("false");
    }

    @Test
    @Order(10)
    @DisplayName("10. Clean-disabled verification: attempting flyway.clean() throws FlywayException")
    void testCleanDisabledVerification() {
        assertThat(primaryFlyway).isNotNull();

        assertThatThrownBy(primaryFlyway::clean)
                .as("flyway.clean() must throw FlywayException when cleanDisabled=true")
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("cleanDisabled");
    }

    @Test
    @Order(11)
    @DisplayName("11. Default commission seeding: seeds initial 10% rate and respects placeholder override")
    void testDefaultCommissionSeedingAndPlaceholderOverride() throws Exception {
        // 1. Verify default 10.0% was seeded into primaryFlyway's public schema
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT rate, created_by, updated_by FROM commission ORDER BY created_at DESC")) {
            assertThat(rs.next())
                    .as("Default commission record must exist after V4 migration")
                    .isTrue();
            assertThat(rs.getDouble("rate")).isEqualTo(10.0);
            assertThat(rs.getString("created_by")).isEqualTo("SYSTEM");
            assertThat(rs.getString("updated_by")).isEqualTo("SYSTEM");
            assertThat(rs.next())
                    .as("Exactly one commission record should be seeded initially")
                    .isFalse();
        }

        // 2. Verify placeholder override with custom rate (e.g. 15.5) in an isolated schema
        String testSchema = "flyway_test_override_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + testSchema);
        }

        try {
            Flyway customFlyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(testSchema)
                    .defaultSchema(testSchema)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .placeholders(Map.of("defaultCommissionRate", "15.5"))
                    .load();

            MigrateResult result = customFlyway.migrate();
            assertThat(result.success).isTrue();

            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT rate, created_by, updated_by FROM " + testSchema + ".commission")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("rate")).isEqualTo(15.5);
                assertThat(rs.getString("created_by")).isEqualTo("SYSTEM");
            }
        } finally {
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
            }
        }
    }

    @Test
    @Order(12)
    @DisplayName("12. Admin dynamic commission update overrides Day-1 seed (MSH-05 / D-17)")
    void testAdminDynamicCommissionUpdateOverridesSeed() throws Exception {
        // Simulates an Admin updating the commission rate in DB (as via setCommissionRate)
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                    INSERT INTO commission (rate, created_at, updated_at, created_by, updated_by)
                    VALUES (25.0, NOW() + INTERVAL '1 second', NOW() + INTERVAL '1 second', 'admin@example.com', 'admin@example.com')
                """);
            }

            // Query latest by created_at DESC matching CommissionRepository.findTopByOrderByCreatedAtDesc
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT rate, created_by FROM commission ORDER BY created_at DESC LIMIT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("rate"))
                        .as("Admin dynamic update must take precedence over Day-1 10% seed")
                        .isEqualTo(25.0);
                assertThat(rs.getString("created_by")).isEqualTo("admin@example.com");
            }
        }
    }
}
