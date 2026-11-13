package com.example.gradproj.EduNest.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "app.scheduling.enabled=false")
@ActiveProfiles({"prod", "test"})
class FlywayDisabledStartupPostgresTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("defaultCommissionRate", "10.0")).load().migrate();
        // outOfOrder(true): since EB-108 added V14 to db/migration, the real
        // migrate() above applies past the unapplied V13 sentinel — and with
        // Flyway's default outOfOrder=false an unapplied lower-version migration
        // is no longer reported as PENDING (CI-verified: probe saw [] instead of
        // ["13"]). The sentinel must still register as pending for the invariant
        // below to be honest: startup (flyway disabled) never executes it.
        var probe = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:startup-migrations")
                .outOfOrder(true)
                .placeholders(Map.of("defaultCommissionRate", "10.0")).load();
        assertThat(probe.info().pending()).extracting(info -> info.getVersion().getVersion()).containsExactly("13");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:startup-migrations");
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void startupDoesNotExecutePendingTestOnlyMigration() {
        assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE version='13'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.flyway_startup_safety_probe') IS NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.eb44_archive_messages') IS NOT NULL", Boolean.class)).isTrue();
    }
}
