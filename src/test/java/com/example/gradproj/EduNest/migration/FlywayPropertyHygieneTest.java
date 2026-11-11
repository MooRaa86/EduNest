package com.example.gradproj.EduNest.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EB-43 / EB-47: Flyway & Production Database Hygiene Test")
public class FlywayPropertyHygieneTest {

    @Test
    @DisplayName("Verify application-prod.properties enforces strict validation, Flyway hardening, and commission seed placeholder")
    void verifyProdPropertiesHygiene() throws IOException {
        Resource resource = new ClassPathResource("application-prod.properties");
        assertThat(resource.exists()).as("application-prod.properties must exist").isTrue();

        Properties props = PropertiesLoaderUtils.loadProperties(resource);

        // DB-01 / DEP-04: ddl-auto=update retired; validate enforced in production
        assertThat(props.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("Production hibernate ddl-auto must be strictly 'validate'")
                .isEqualTo("validate");

        // DEP-04: show-sql disabled in production
        assertThat(props.getProperty("spring.jpa.show-sql"))
                .as("Production show-sql must be strictly 'false'")
                .isEqualTo("false");

        // Production safety: flyway clean must be permanently disabled
        assertThat(props.getProperty("spring.flyway.clean-disabled"))
                .as("Production flyway clean-disabled must be 'true'")
                .isEqualTo("true");

        // Flyway migration configuration
        assertThat(props.getProperty("spring.flyway.enabled"))
                .as("Flyway must be disabled during normal production startup")
                .isEqualTo("false");
        assertThat(props.getProperty("spring.flyway.out-of-order")).isEqualTo("false");

        // Production safety: baseline-on-migrate must be false by default to prevent silent baselining of drifted DBs
        assertThat(props.getProperty("spring.flyway.baseline-on-migrate"))
                .as("Flyway baseline-on-migrate must be 'false' in production by default")
                .isEqualTo("false");

        assertThat(props.getProperty("spring.flyway.baseline-version"))
                .as("Flyway baseline-version must be '1'")
                .isEqualTo("1");

        // EB-47 / P3-05: Commission seed configuration and placeholder
        assertThat(props.getProperty("app.commission.default"))
                .as("app.commission.default must be set in production")
                .isNotNull();
        assertThat(props.getProperty("spring.flyway.placeholders.defaultCommissionRate"))
                .as("spring.flyway.placeholders.defaultCommissionRate must be set in production")
                .isEqualTo("${app.commission.default}");
    }

    @Test
    @DisplayName("Verify base application.properties enforces validation, Flyway defaults, and commission seed placeholder")
    void verifyBasePropertiesHygiene() throws IOException {
        Resource resource = new ClassPathResource("application.properties");
        assertThat(resource.exists()).as("application.properties must exist").isTrue();

        Properties props = PropertiesLoaderUtils.loadProperties(resource);

        assertThat(props.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("Base hibernate ddl-auto must be strictly 'validate'")
                .isEqualTo("validate");

        assertThat(props.getProperty("spring.jpa.show-sql"))
                .as("Base show-sql must be strictly 'false'")
                .isEqualTo("false");

        assertThat(props.getProperty("spring.flyway.enabled"))
                .as("Flyway must be disabled during normal base startup")
                .isEqualTo("false");
        assertThat(props.getProperty("spring.flyway.out-of-order")).isEqualTo("false");

        assertThat(props.getProperty("spring.flyway.clean-disabled"))
                .as("Flyway clean-disabled must be 'true' in base properties")
                .isEqualTo("true");

        assertThat(props.getProperty("spring.flyway.baseline-on-migrate"))
                .as("Flyway baseline-on-migrate must be 'false' in base properties")
                .isEqualTo("false");

        // EB-47 / P3-05: Commission seed configuration and placeholder
        assertThat(props.getProperty("app.commission.default"))
                .as("app.commission.default must be set in base properties")
                .isNotNull();
        assertThat(props.getProperty("spring.flyway.placeholders.defaultCommissionRate"))
                .as("spring.flyway.placeholders.defaultCommissionRate must be set in base properties")
                .isEqualTo("${app.commission.default}");
    }

    @Test
    @DisplayName("Verify test profile isolates H2 in-memory and disables Flyway (D-26 / D-27)")
    void verifyTestProfileIsolation() throws IOException {
        Resource resource = new ClassPathResource("application-test.properties");
        assertThat(resource.exists()).as("application-test.properties must exist").isTrue();

        Properties props = PropertiesLoaderUtils.loadProperties(resource);

        assertThat(props.getProperty("spring.flyway.enabled"))
                .as("Flyway must be disabled in test profile for H2 in-memory suite")
                .isEqualTo("false");

        assertThat(props.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("H2 test profile must use create-drop for clean isolated test runs")
                .isEqualTo("create-drop");

        assertThat(props.getProperty("app.commission.default"))
                .as("app.commission.default must be set in test profile")
                .isEqualTo("10.0");
    }

    @Test
    @DisplayName("Verify Flyway migration scripts exist on classpath and inert script.sql is deleted")
    void verifyMigrationScripts() {
        Resource v1 = new ClassPathResource("db/migration/V1__initial_schema.sql");
        assertThat(v1.exists()).as("V1__initial_schema.sql must exist on classpath").isTrue();

        Resource v2 = new ClassPathResource("db/migration/V2__mentorship_explore_trigram_index.sql");
        assertThat(v2.exists()).as("V2__mentorship_explore_trigram_index.sql must exist on classpath").isTrue();

        Resource v3 = new ClassPathResource("db/migration/V3__uniqueness_constraints_and_dedupe.sql");
        assertThat(v3.exists()).as("V3__uniqueness_constraints_and_dedupe.sql must exist on classpath").isTrue();

        Resource v4 = new ClassPathResource("db/migration/V4__seed_default_commission.sql");
        assertThat(v4.exists()).as("V4__seed_default_commission.sql must exist on classpath").isTrue();

        // DB-02: verify inert script.sql has been removed from source tree and runtime classpath
        File sourceScript = new File("src/main/resources/scripts/script.sql");
        assertThat(sourceScript.exists())
                .as("src/main/resources/scripts/script.sql source file must be deleted (DB-02)")
                .isFalse();

        Resource inertScript = new ClassPathResource("scripts/script.sql");
        assertThat(inertScript.exists())
                .as("scripts/script.sql must not exist on runtime classpath (DB-02)")
                .isFalse();
    }
}
