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

        // 1. Verify Project Identity
        boolean projectMatches = dbUrl.contains(EXPECTED_PROJECT_REF) || 
                                (dbUser != null && dbUser.contains(EXPECTED_PROJECT_REF));
        assertThat(projectMatches)
                .as("DATABASE_URL or DATABASE_NAME must match authorized project ref: " + EXPECTED_PROJECT_REF)
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
