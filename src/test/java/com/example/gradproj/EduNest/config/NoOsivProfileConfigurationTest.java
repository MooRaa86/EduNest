package com.example.gradproj.EduNest.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies config-data isolation without starting the application or trialing endpoints. */
class NoOsivProfileConfigurationTest {
    private static final String OSIV = "spring.jpa.open-in-view";
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void noOsivProfileIsExplicitlyOptIn() {
        for (String profiles : new String[]{"test", "prod,test"}) {
            context.withPropertyValues("spring.profiles.active=" + profiles).run(app -> {
                assertThat(app.getEnvironment().acceptsProfiles("no-osiv")).isFalse();
                assertThat(app.getEnvironment().getProperty(OSIV)).isNull();
            });
        }
        context.run(app -> {
            assertThat(app.getEnvironment().acceptsProfiles("no-osiv")).isFalse();
            assertThat(app.getEnvironment().getProperty(OSIV)).isNull();
        });
    }

    @Test
    void explicitNoOsivProfileSetsPropertyFalse() {
        context.withPropertyValues("spring.profiles.active=prod,test,no-osiv").run(app -> {
            assertThat(app.getEnvironment().acceptsProfiles("no-osiv")).isTrue();
            assertThat(app.getEnvironment().getProperty(OSIV, Boolean.class)).isFalse();
        });
    }
}
