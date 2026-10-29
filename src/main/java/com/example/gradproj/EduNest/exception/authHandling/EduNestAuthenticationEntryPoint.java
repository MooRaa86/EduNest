package com.example.gradproj.EduNest.exception.authHandling;

import com.example.gradproj.EduNest.exception.ApiErrorCode;
import com.example.gradproj.EduNest.exception.ServletErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * P1-05 / EB-12 — unified 401 envelope. The Spring framework message is
 * NOT echoed; a clean, stable text is sent instead (the FE renders it
 * directly). Same shape/code as every other producer (ServletErrorWriter).
 */
@Component
public class EduNestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        ServletErrorWriter.write(response,
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorCode.ERR_UNAUTHENTICATED,
                "Authentication required. Please log in.",
                request.getRequestURI());
    }
}
