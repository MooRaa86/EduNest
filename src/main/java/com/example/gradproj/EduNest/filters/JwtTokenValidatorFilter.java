package com.example.gradproj.EduNest.filters;

import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.service.jwt.JwtServiceI;
import com.example.gradproj.EduNest.utils.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                      "error": "Invalid token: user is deactivated, please login again"
                    }
                    """);
            return;

        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                      "error": "Invalid JWT token"
                    }
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return request.getServletPath().equals("/login-api");
    }
}
