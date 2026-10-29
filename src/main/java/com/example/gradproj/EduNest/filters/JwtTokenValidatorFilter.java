package com.example.gradproj.EduNest.filters;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * P1-05 / EB-12 — JWT-validation 401s now use the unified error envelope
 * (same shape + ERR_INVALID_TOKEN as the advice-layer InvalidJwtToken
 * handler). The two distinct friendly messages are preserved:
 * deactivation/deletion vs structurally invalid token — only the shape,
 * never the semantics, changed.
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
        } catch (InvalidJwtToken ex){
            ServletErrorWriter.write(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    ApiErrorCode.ERR_INVALID_TOKEN,
                    "Invalid token: user is deactivated or deleted, please login again",
                    request.getRequestURI());
            return;

        } catch (Exception ex) {
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
        return request.getServletPath().equals("/login-api");
    }
}
