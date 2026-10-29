package com.example.gradproj.EduNest.config.security;

import com.example.gradproj.EduNest.exception.authHandling.EduNestAccessDeniedHandler;
import com.example.gradproj.EduNest.exception.authHandling.EduNestAuthenticationEntryPoint;
import com.example.gradproj.EduNest.filters.JwtTokenGeneratorFilter;
import com.example.gradproj.EduNest.filters.JwtTokenValidatorFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-02 / SEL-02 — the boot matrix. Exactly one of three outcomes must hold:
 *  1. prod profile            -> boots the stateless JWT chain
 *  2. nothing selected        -> IllegalStateException, refuses to boot
 *  3. EDUNEST_UNSAFE_DEV=true -> boots (insecure dev chain by explicit choice)
 *
 * Security beans (filters/handlers) are mock stand-ins: this matrix only judges the
 * profile/flag decision, not JWT semantics (those have their own tests).
 */
class SecurityProfileBootMatrixTest {

    private WebApplicationContextRunner runner(String... props) {
        WebApplicationContextRunner r = new WebApplicationContextRunner()
                .withUserConfiguration(ProjectSecurityConfig.class)
                .withBean("mvcHandlerMappingIntrospector", org.springframework.web.servlet.handler.HandlerMappingIntrospector.class,
                        () -> new org.springframework.web.servlet.handler.HandlerMappingIntrospector())
                .withBean(JwtTokenGeneratorFilter.class, () -> Mockito.mock(JwtTokenGeneratorFilter.class))
                .withBean(JwtTokenValidatorFilter.class, () -> Mockito.mock(JwtTokenValidatorFilter.class))
                .withBean(EduNestAuthenticationEntryPoint.class, () -> Mockito.mock(EduNestAuthenticationEntryPoint.class))
                .withBean(EduNestAccessDeniedHandler.class, () -> Mockito.mock(EduNestAccessDeniedHandler.class))
                .withBean(org.springframework.security.core.userdetails.UserDetailsService.class,
                        () -> Mockito.mock(org.springframework.security.core.userdetails.UserDetailsService.class))
                // EB-15: ProjectSecurityConfig now takes the throttling filter,
                // which needs (properties, lockout service, Jackson mapper).
                .withBean(com.example.gradproj.EduNest.config.security.RateLimitProperties.class,
                        com.example.gradproj.EduNest.config.security.RateLimitProperties::new)
                .withBean(com.example.gradproj.EduNest.service.auth.LoginLockoutService.class,
                        () -> new com.example.gradproj.EduNest.service.auth.LoginLockoutService(
                                new com.example.gradproj.EduNest.config.security.RateLimitProperties()))
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,
                        com.fasterxml.jackson.databind.ObjectMapper::new)
                .withBean(com.example.gradproj.EduNest.filters.AuthRateLimitFilter.class,
                        () -> new com.example.gradproj.EduNest.filters.AuthRateLimitFilter(
                                new com.example.gradproj.EduNest.config.security.RateLimitProperties(),
                                new com.example.gradproj.EduNest.service.auth.LoginLockoutService(
                                        new com.example.gradproj.EduNest.config.security.RateLimitProperties()),
                                new com.fasterxml.jackson.databind.ObjectMapper()));
        if (props.length > 0) {
            r = r.withPropertyValues(props);
        }
        return r;
    }

    /** walks the cause chain to the leaf exception (the config constructor's own error). */
    private Throwable rootCause(Throwable failure) {
        InvocationTargetException i;
        Throwable t = failure;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    @Test
    @DisplayName("prod profile -> boots, single SecurityFilterChain present")
    void prodBoots() {
        runner("spring.profiles.active=prod").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
        });
    }

    @Test
    @DisplayName("no profile and no flag -> refuses to boot with explicit reason")
    void nothingSelectedRefusesToBoot() {
        runner().run(ctx -> {
            assertThat(ctx).hasFailed();
            IllegalStateException cause = (IllegalStateException) rootCause(ctx.getStartupFailure());
            assertThat(cause.getMessage()).contains("Refusing to start").contains("EDUNEST_UNSAFE_DEV");
        });
    }

    @Test
    @DisplayName("EDUNEST_UNSAFE_DEV=true (no profile) -> boots by explicit choice")
    void unsafeDevBootsExplicitly() {
        runner("EDUNEST_UNSAFE_DEV=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
        });
    }

    @Test
    @DisplayName("prod + unsafe flag -> still the secure chain (flag ignored, boots)")
    void prodWinsOverUnsafeFlag() {
        runner("spring.profiles.active=prod", "EDUNEST_UNSAFE_DEV=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
        });
    }
}
