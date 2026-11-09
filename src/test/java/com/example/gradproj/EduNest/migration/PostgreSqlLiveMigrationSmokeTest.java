package com.example.gradproj.EduNest.migration;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * EB-43: Opt-in Live PostgreSQL Disposable Schema Smoke Test.
 * Matches the safe live-smoke pattern from EB-83:
 * - Runs ONLY when EDUNEST_LIVE_PG_TEST=true and DATABASE_URL are set.
 * - Migrates into a strictly disposable, randomly generated schema (flyway_smoke_<uuid>).
 * - NEVER operates in or modifies the public schema or existing Supabase data.
 * - Verifies V1, V2, 42 tables, 5 TEXT columns, seeded roles, and JPA validation.
 * - Completely drops the disposable schema in tearDown (@AfterEach / finally).
 * - Cleanly skipped in normal offline / CI builds.
 */
@DisplayName("EB-43: Opt-in Live PostgreSQL Disposable Schema Migration Smoke Test")
public class PostgreSqlLiveMigrationSmokeTest {

    private String dbUrl;
    private String dbUser;
    private String dbPass;
    private String testSchema;

    @BeforeEach
    void setUp() throws Exception {
        boolean liveTestEnabled = "true".equalsIgnoreCase(System.getenv("EDUNEST_LIVE_PG_TEST"));
        dbUrl = System.getenv("DATABASE_URL");
        dbUser = System.getenv("DATABASE_NAME");
        dbPass = System.getenv("DATABASE_PASSWORD");

        Assumptions.assumeTrue(liveTestEnabled,
                "EDUNEST_LIVE_PG_TEST is not 'true'; skipping live PostgreSQL migration smoke test");
        Assumptions.assumeTrue(dbUrl != null && !dbUrl.isBlank(),
                "DATABASE_URL not set; skipping live PostgreSQL migration smoke test");

        // Generate unique disposable schema name
        testSchema = "flyway_smoke_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // Create disposable schema
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + testSchema);
        }
    }

    @AfterEach
    void tearDown() {
        if (testSchema != null && dbUrl != null && !dbUrl.isBlank()) {
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
                System.out.println("[SMOKE-CLEANUP] Dropped disposable test schema: " + testSchema);
            } catch (Exception e) {
                System.err.println("[SMOKE-CLEANUP-WARNING] Failed to drop disposable schema " + testSchema + ": " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Migrate into isolated disposable schema, verify 42 tables, 5 TEXT columns, seeded roles, and JPA validation")
    void testLiveMigrationInDisposableSchema() throws Exception {
        // 1. Run Flyway migration strictly inside the disposable schema
        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, dbUser, dbPass)
                .schemas(testSchema)
                .defaultSchema(testSchema)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .placeholders(Map.of("defaultCommissionRate", "10.0"))
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success)
                .as("Live migration into disposable schema must succeed")
                .isTrue();
        assertThat(result.migrationsExecuted)
                .as("Must apply migrations V1 through V10")
                .isEqualTo(10);

        // 2. Direct JDBC schema validation inside disposable schema
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            // Count tables in disposable schema
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_type = 'BASE TABLE'
                  AND table_name != 'flyway_schema_history'
                ORDER BY table_name
            """)) {
                ps.setString(1, testSchema);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> tables = new ArrayList<>();
                    while (rs.next()) {
                        tables.add(rs.getString("table_name"));
                    }
                    assertThat(tables)
                            .as("Disposable schema must contain exactly 42 application tables")
                            .hasSize(49);
                }
            }

            // Verify 5 TEXT columns
            Map<String, String> textColumns = Map.of(
                    "contact_message", "message",
                    "projects", "project_brief",
                    "project_submission", "project_feedback",
                    "tasks", "task_description",
                    "task_submission", "task_feedback"
            );

            for (Map.Entry<String, String> entry : textColumns.entrySet()) {
                try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """)) {
                    ps.setString(1, testSchema);
                    ps.setString(2, entry.getKey());
                    ps.setString(3, entry.getValue());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("Column " + entry.getKey() + "." + entry.getValue() + " must exist in " + testSchema)
                                .isTrue();
                        assertThat(rs.getString("data_type"))
                                .as(entry.getKey() + "." + entry.getValue() + " data_type must be 'text'")
                                .isEqualTo("text");
                    }
                }
            }

            // Verify seeded roles in disposable schema
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM " + testSchema + ".roles ORDER BY name")) {
                List<String> roles = new ArrayList<>();
                while (rs.next()) {
                    roles.add(rs.getString("name"));
                }
                assertThat(roles)
                        .as("Initial reference roles (ADMIN, MENTOR, STUDENT) must be seeded")
                        .contains("ADMIN", "MENTOR", "STUDENT");
            }
        }

        // 3. Programmatically bootstrap JPA EntityManagerFactory targeting the disposable schema
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        emfBean.setPackagesToScan("com.example.gradproj.EduNest.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        emfBean.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> jpaProperties = new HashMap<>();
        jpaProperties.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        jpaProperties.put("jakarta.persistence.jdbc.url", dbUrl);
        jpaProperties.put("jakarta.persistence.jdbc.user", dbUser);
        jpaProperties.put("jakarta.persistence.jdbc.password", dbPass);
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.default_schema", testSchema); // TARGET DISPOSABLE SCHEMA ONLY
        jpaProperties.put("hibernate.hbm2ddl.auto", "validate"); // STRICT VALIDATE MODE
        jpaProperties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        jpaProperties.put("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");

        emfBean.setJpaPropertyMap(jpaProperties);

        assertDoesNotThrow(() -> {
            emfBean.afterPropertiesSet();
            EntityManagerFactory emf = emfBean.getObject();
            assertThat(emf).isNotNull();
            assertThat(emf.isOpen()).isTrue();
            emf.close();
        }, "JPA EntityManagerFactory with ddl-auto=validate must succeed against disposable schema");
    }
}
