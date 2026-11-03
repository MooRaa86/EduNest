package com.example.gradproj.EduNest.migration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("EB-82: Live PostgreSQL Schema Validation & Query Compatibility Test")
public class PostgreSqlLiveSchemaValidationTest {

    private static final String EXPECTED_PROJECT_REF = "ciogysvcykzxrjuskxmk";

    @Test
    @DisplayName("Validate all EduNest JPA Entities against Live Supabase PostgreSQL with ddl-auto=validate")
    void validateSchemaAgainstLivePostgreSql() {
        String dbUrl = System.getenv("DATABASE_URL");
        String dbUser = System.getenv("DATABASE_NAME");
        String dbPass = System.getenv("DATABASE_PASSWORD");

        Assumptions.assumeTrue(dbUrl != null && !dbUrl.isBlank(),
                "DATABASE_URL not set in process environment; skipping live PostgreSQL test");

        // 1. Verify Project Identity and SSL Configuration
        boolean projectMatches = (dbUrl != null && dbUrl.contains(EXPECTED_PROJECT_REF)) ||
                                (dbUser != null && dbUser.contains(EXPECTED_PROJECT_REF));
        assertThat(projectMatches)
                .as("DATABASE_URL or DATABASE_NAME must match authorized project ref: " + EXPECTED_PROJECT_REF)
                .isTrue();

        assertThat(dbUrl)
                .as("DATABASE_URL must be a PostgreSQL JDBC URL")
                .startsWith("jdbc:postgresql://");

        assertThat(dbUrl)
                .as("Insecure sslmode detected; sslmode=disable/allow/prefer is forbidden")
                .doesNotContain("sslmode=disable")
                .doesNotContain("sslmode=allow")
                .doesNotContain("sslmode=prefer");

        boolean hasSslParam = dbUrl.contains("sslmode=require") ||
                              dbUrl.contains("sslmode=verify-ca") ||
                              dbUrl.contains("sslmode=verify-full") ||
                              dbUrl.contains("ssl=true");
        assertThat(hasSslParam)
                .as("DATABASE_URL must enforce SSL encryption (e.g. sslmode=require)")
                .isTrue();

        // 2. Perform direct JDBC read-only pre-assertion
        assertDoesNotThrow(() -> {
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                conn.setReadOnly(true);
                try (Statement st = conn.createStatement()) {
                    st.execute("SET default_transaction_read_only = on");
                    try (ResultSet rs = st.executeQuery("SELECT current_database(), current_schema(), current_setting('transaction_read_only')")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString(1)).isEqualTo("postgres");
                        assertThat(rs.getString(2)).isEqualTo("public");
                        assertThat(rs.getString(3)).isEqualTo("on");
                    }

                    // Query actual metadata of public.admin to verify column naming in database
                    try (ResultSet rs = st.executeQuery("""
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'admin' AND column_name = 'created_at'
                    """)) {
                        assertThat(rs.next())
                                .as("public.admin table must have created_at column in PostgreSQL")
                                .isTrue();
                    }

                    // Diagnostic check: inspect pg_stat_ssl for the upstream PostgreSQL backend connection.
                    // Note: In Supabase pooler architecture (Supavisor), client-to-pooler encryption is strictly
                    // enforced by JDBC sslmode=require, while pooler-to-backend connections within the private VPC
                    // may report ssl=false in pg_stat_ssl.
                    try (ResultSet rs = st.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()")) {
                        if (rs.next()) {
                            boolean upstreamSsl = rs.getBoolean("ssl");
                            System.out.println("[DIAGNOSTIC] Upstream Supabase backend connection ssl status in pg_stat_ssl: " + upstreamSsl);
                        }
                    } catch (Exception ignored) {
                        // Diagnostic only; client-to-pooler TLS is strictly enforced by pgJDBC sslmode=require
                    }
                }
            }
        }, "JDBC connection, session read-only setting, and project sanity check must succeed");

        // 3. Programmatically bootstrap JPA EntityManagerFactory with ddl-auto=validate
        // Strictly isolates JPA schema validation: No web server, no RolesSeeder, no OtpCleanupService, no scheduled tasks
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
        jpaProperties.put("hibernate.hbm2ddl.auto", "validate"); // STRICT VALIDATE MODE - NEVER UPDATE OR CREATE
        jpaProperties.put("hibernate.connection.read_only", "true"); // ENFORCE READ-ONLY ON JDBC LEVEL
        jpaProperties.put("hibernate.connection.init_sql", "SET default_transaction_read_only = on;"); // SESSION-LEVEL READ-ONLY

        // Align with Spring Boot production naming strategy defaults
        jpaProperties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        jpaProperties.put("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");

        emfBean.setJpaPropertyMap(jpaProperties);
        emfBean.afterPropertiesSet();

        EntityManagerFactory emf = emfBean.getObject();
        assertThat(emf).isNotNull();

        try {
            EntityManager em = emf.createEntityManager();
            assertThat(em.isOpen()).isTrue();

            // 4. Test PostgreSQL native query with string_agg in read-only transaction
            Object result = em.createNativeQuery("""
                SELECT
                    (SELECT string_agg(CAST(s.id AS text), ',') FROM sessions s
                     JOIN session_attendance_result sar ON sar.session_id = s.id
                     WHERE s.week_id = -1 AND sar.student_id = -1 AND sar.attended = true) as attendedSessions
            """).getSingleResult();

            // Query executed cleanly on PostgreSQL engine without syntax error
            assertThat(result == null || result instanceof String).isTrue();

            em.close();
        } finally {
            emf.close();
        }
    }
}
