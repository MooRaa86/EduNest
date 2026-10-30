package com.example.gradproj.EduNest.config.security;

import com.example.gradproj.EduNest.config.auth.EduNestAuthenticationProvider;
import com.example.gradproj.EduNest.config.file.UploadPolicy;
import com.example.gradproj.EduNest.exception.authHandling.EduNestAccessDeniedHandler;
import com.example.gradproj.EduNest.exception.authHandling.EduNestAuthenticationEntryPoint;
import com.example.gradproj.EduNest.filters.AuthRateLimitFilter;
import com.example.gradproj.EduNest.filters.JwtTokenGeneratorFilter;
import com.example.gradproj.EduNest.filters.JwtTokenValidatorFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * THE single security configuration (P1-02 / SEL-02).
 *
 * Boot contract (fail-fast, mirrors JwtSecretProvider's philosophy):
 *  - "prod" active           -> PROD branch: stateless JWT chain, explicit route matrix.
 *  - any other/no profile    -> REFUSES TO START unless EDUNEST_UNSAFE_DEV=true is set
 *                               explicitly; the permissive dev branch is then built with a
 *                               loud WARN banner. Booting insecurely by omission is
 *                               impossible.
 *
 * Exactly one SecurityFilterChain / PasswordEncoder / AuthenticationManager / CORS
 * source exists again — the previous two-class split silently chose behavior by
 * profile presence and duplicated security beans (drift risk).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ProjectSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ProjectSecurityConfig.class);

    /** Explicit opt-in for the insecure local-dev chain. Never set in staging/prod. */
    public static final String UNSAFE_DEV_ENV = "EDUNEST_UNSAFE_DEV";

    private final Environment env;

    private final JwtTokenGeneratorFilter jwtTokenGeneratorFilter;
    private final JwtTokenValidatorFilter jwtTokenValidatorFilter;
    private final EduNestAuthenticationEntryPoint customAuthEntryPoint;
    private final EduNestAccessDeniedHandler customAccessDeniedHandler;
    private final AuthRateLimitFilter authRateLimitFilter;

    public ProjectSecurityConfig(
            Environment env,
            JwtTokenGeneratorFilter jwtTokenGeneratorFilter,
            JwtTokenValidatorFilter jwtTokenValidatorFilter,
            EduNestAuthenticationEntryPoint customAuthEntryPoint,
            EduNestAccessDeniedHandler customAccessDeniedHandler,
            AuthRateLimitFilter authRateLimitFilter
    ) {
        this.env = env;
        this.jwtTokenGeneratorFilter = jwtTokenGeneratorFilter;
        this.jwtTokenValidatorFilter = jwtTokenValidatorFilter;
        this.customAuthEntryPoint = customAuthEntryPoint;
        this.customAccessDeniedHandler = customAccessDeniedHandler;
        this.authRateLimitFilter = authRateLimitFilter;

        boolean prod = isProd();
        boolean unsafeDev = isUnsafeDevRequested();

        if (prod && unsafeDev) {
            log.warn("EDUNEST_UNSAFE_DEV=true is ignored while the 'prod' profile is active — the secure JWT chain is in force.");
        }
        if (!prod && !unsafeDev) {
            throw new IllegalStateException(
                    "Refusing to start: no security mode selected. Set SPRING_PROFILES_ACTIVE=prod for real environments, "
                            + "or explicitly set EDUNEST_UNSAFE_DEV=true for local development (insecure permissive chain). "
                            + "Silent insecure boots are not allowed (SEL-02)."
            );
        }
        if (prod) {
            log.info("Security: PROD profile active — stateless JWT chain with authorization matrix.");
        } else {
            log.warn(String.format(
                    "%n%n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!%n"
                            + "!! EDUNEST IS RUNNING IN INSECURE DEV MODE (EDUNEST_UNSAFE_DEV)   !!%n"
                            + "!! - every endpoint is public, CSRF off, sessions + basic auth on !!%n"
                            + "!! NEVER run this mode in staging/production.                     !!%n"
                            + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!%n"
            ));
        }
    }

    private boolean isProd() {
        return Arrays.asList(env.getActiveProfiles()).contains("prod");
    }

    private boolean isUnsafeDevRequested() {
        String flag = env.getProperty(UNSAFE_DEV_ENV);
        return flag != null && "true".equalsIgnoreCase(flag.trim());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return isProd() ? prodChain(http) : devChain(http);
    }

    /**
     * PROD branch — stateless JWT, explicit authorization matrix and custom
     * 401/403 JSON handlers, including the current upload exposure policy.
     */
    private SecurityFilterChain prodChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(smc
                        -> smc.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(corsConfig
                        -> corsConfig.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration config = new CorsConfiguration();
                        config.setAllowedOriginPatterns(Arrays.asList("*"));
                        config.setAllowedMethods(Collections.singletonList("*"));
                        config.setAllowCredentials(true);
                        config.setAllowedHeaders(Collections.singletonList("*"));
                        config.setExposedHeaders(Arrays.asList("Authorization"));
                        config.setMaxAge(3600L);
                        return config;
                    }
                }))
                .csrf(csrf -> csrf.disable())
                .addFilterAfter(authRateLimitFilter, HeaderWriterFilter.class)
                .addFilterAfter(jwtTokenGeneratorFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(jwtTokenValidatorFilter, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // ========== Public APIs ==========
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/api/auth/**",
                                "/api/v1/register/**",
                                "/login-api",
                                "/forget-password/**",
                                "/api/v1/admin/register",
                                "/ws/**",

                                "/api/v1/contact/save-message",
                                "/api/v1/student/mentorships/*/overview",
                                "/api/v1/student/mentorships/*/reviews",
                                "/api/v1/mentorship/explore",
                                "/api/v1/mentorship/categories",
                                "/api/v1/profile/mentor/**"
                        ).permitAll()
                        // ==== Upload exposure policy (P1-21 / EB-28, FILE-01,
                        // TD-09/G14, D-07) — ONLY the anonymous-public categories
                        // leave the chain. The former wrong-name entries
                        // (profile-images, student-profile, mentor-profile) and the
                        // duplicated public upload catch-alls are gone; private
                        // categories are denyAll'd below and reachable ONLY through
                        // ownership-checked /files/** typed endpoints. ====
                        .requestMatchers(UploadPolicy.PUBLIC_UPLOAD_PATTERNS.toArray(new String[0])).permitAll()
                        // EVERYTHING ELSE under /uploads/** is non-public (fail-closed):
                        // submissions/task-attachment/project-attachment, admin, unknown
                        // categories, future additions — never served via /uploads/**,
                        // not even for an authenticated user. /files/** typed endpoints stay
                        // ownership-checked via anyRequest().authenticated() + FileAccessService.
                        .requestMatchers("/uploads/**").denyAll()
                        // ========== ADMIN Only ==========
                        .requestMatchers(
                                "/api/v1/admin/**",
                                "/admin/**",
                                "/api/admin/**",
                                "/api/users/*/badges",
                                "/api/v1/contact/all-messages",
                                "/api/v1/contact/message/**",
                                "/api/v1/contact/messages/**"
                        ).hasRole("ADMIN")
                        // ========== Authenticated Users (Both MENTOR & STUDENT) ==========
                        .requestMatchers(
                                "/settings/**",
                                "/api/v1/notifications/**",
                                "/student/profile/**",
                                "/api/v1/file/**"
                        ).authenticated()

                        // ========== Any remaining requests require authentication ==========
                        .anyRequest().authenticated()
                );
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(customAuthEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
        );
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * DEV branch (EDUNEST_UNSAFE_DEV=true only) — permissive for local development,
     * except for the explicit upload policy below.
     */
    private SecurityFilterChain devChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(UploadPolicy.PUBLIC_UPLOAD_PATTERNS.toArray(new String[0])).permitAll()
                        .requestMatchers("/uploads/**").denyAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/api/auth/**",
                                "/api/v1/**",
                                "/ws/**",
                                "/api/v1/contact/**",
                                "/uploads/mentorship-cover-images/**",
                                "/uploads/profile-images/**",
                                "/uploads/student-profile/**",
                                "/uploads/mentor-profile/**",
                                "/files/**"
                        ).permitAll()
                        .anyRequest().permitAll()
                );
        http.httpBasic(Customizer.withDefaults());
        http.formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * EB-15: the rate-limit filter is ONLY a security-chain member (registered
     * via addFilterAfter(HeaderWriterFilter) — an always-registered built-in
     * slot near the top of the chain; a custom filter class cannot anchor
     * addFilterBefore/After because it has no global order slot). This
     * registration bean turns OFF Spring Boot's automatic plain-servlet
     * registration of the @Component filter — otherwise every gated request
     * could be counted twice. The round-trip guard (COUNTED_ATTR) is defense
     * in depth for any scripting environment that bypasses this.
     */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Dev-mode CORS source (consulted by the dev branch via `cors(Customizer.withDefaults())`). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            com.example.gradproj.EduNest.service.auth.LoginLockoutService lockoutService
    ) {
        AuthenticationProvider authProvider = new EduNestAuthenticationProvider(
                userDetailsService, passwordEncoder, lockoutService);
        ProviderManager providerManager = new ProviderManager(authProvider);
        providerManager.setEraseCredentialsAfterAuthentication(true);
        return providerManager;
    }
}
