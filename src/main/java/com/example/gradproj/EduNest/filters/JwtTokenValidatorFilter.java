package com.example.gradproj.EduNest.filters;

import com.example.gradproj.EduNest.config.security.PublicRoutesPolicy;
import com.example.gradproj.EduNest.exception.ApiErrorCode;
import com.example.gradproj.EduNest.exception.ServletErrorWriter;
import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.service.jwt.JwtServiceI;
import com.example.gradproj.EduNest.utils.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * P1-03 / EB-10 (SEL-16) & P1-05 / EB-12:
 * Validates incoming JWT tokens and populates the SecurityContext.
 *
 * If a stale or invalid token is supplied to a genuinely public route
 * (defined in PublicRoutesPolicy), the filter gracefully proceeds anonymously
 * instead of returning 401, allowing the public endpoint to serve the request.
 * Protected endpoints continue to reject invalid tokens with 401 ERR_INVALID_TOKEN.
 */
@RequiredArgsConstructor
@Component
public class JwtTokenValidatorFilter extends OncePerRequestFilter {
    private final JwtServiceI jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String jwtToken = request.getHeader(Constants.JWT_HEADER);
        if (jwtToken == null || jwtToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            jwtService.validateToken(jwtToken);
        } catch (InvalidJwtToken ex) {
            if (PublicRoutesPolicy.isPublicRoute(request)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            ServletErrorWriter.write(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    ApiErrorCode.ERR_INVALID_TOKEN,
                    "Invalid token: user is deactivated or deleted, please login again",
                    request.getRequestURI());
            return;

        } catch (Exception ex) {
            if (PublicRoutesPolicy.isPublicRoute(request)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            ServletErrorWriter.write(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    ApiErrorCode.ERR_INVALID_TOKEN,
                    "Invalid JWT token, please login again",
                    request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return "/login-api".equals(PublicRoutesPolicy.getPathWithinApplication(request));
    }
}
