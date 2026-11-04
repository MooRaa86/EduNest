package com.example.gradproj.EduNest.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.support.TestPropertySourceUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingConfigurationTest {
    @Test
    void schedulingEnabledByDefault() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SchedulingConfiguration.class);
            context.refresh();
            assertFalse(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty());
        }
    }

    @Test
    void schedulingCanBeDisabledForVerification() {
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "app.scheduling.enabled=false");
            context.register(SchedulingConfiguration.class);
            context.refresh();
            assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty());
        }
    }
}
