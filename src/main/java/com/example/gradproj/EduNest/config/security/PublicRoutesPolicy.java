package com.example.gradproj.EduNest.config.security;

import com.example.gradproj.EduNest.config.file.UploadPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UrlPathHelper;

import java.util.List;
import java.util.stream.Stream;

/**
 * P1-03 / EB-10 — Single source of truth for public (permitAll) routes (SEL-16).
 *
 * Both the production security chain (ProjectSecurityConfig#prodChain) and the
 * JWT validation filter (JwtTokenValidatorFilter) MUST reference these constants.
 *
 * Any route in {@code ALL_PUBLIC_PATTERNS} is accessible anonymously. If a client
 * mistakenly sends a stale, expired, or malformed Authorization header to a public
 * route, the JWT validator gracefully ignores the invalid token instead of failing
 * fast with 401, allowing the request to proceed to the public endpoint.
 */
public final class PublicRoutesPolicy {

    /**
     * Public API & documentation endpoints.
     */
    public static final List<String> PUBLIC_API_PATTERNS = List.of(
            "/swagger-ui/**",
            "/v3/api-docs",
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
    );

    /**
     * Complete allowlist of all anonymous-public routes (APIs + public upload folders).
     */
    public static final List<String> ALL_PUBLIC_PATTERNS = Stream.concat(
            PUBLIC_API_PATTERNS.stream(),
            UploadPolicy.PUBLIC_UPLOAD_PATTERNS.stream()
    ).toList();

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    /**
     * Extracts the application-relative request path safely across MockMvc,
     * standalone servlet containers, and context-path configurations.
     */
    public static String getPathWithinApplication(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String path = PATH_HELPER.getPathWithinApplication(request);
        if (path == null || path.isBlank()) {
            path = request.getServletPath();
        }
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        return path != null ? path : "";
    }

    /**
     * Checks if the given HTTP request targets an anonymous-public pattern.
     */
    public static boolean isPublicRoute(HttpServletRequest request) {
        return isPublicRoute(getPathWithinApplication(request));
    }

    /**
     * Checks if the given servlet path matches any anonymous-public pattern.
     *
     * @param servletPath the request servlet path (e.g., from request.getServletPath())
     * @return true if the path is permitted to anonymous users
     */
    public static boolean isPublicRoute(String servletPath) {
        if (servletPath == null || servletPath.isBlank()) {
            return false;
        }
        String normalized = (servletPath.length() > 1 && servletPath.endsWith("/"))
                ? servletPath.substring(0, servletPath.length() - 1)
                : servletPath;

        for (String pattern : ALL_PUBLIC_PATTERNS) {
            if (MATCHER.match(pattern, servletPath) || MATCHER.match(pattern, normalized)) {
                return true;
            }
        }
        return false;
    }

    private PublicRoutesPolicy() {
    }
}
