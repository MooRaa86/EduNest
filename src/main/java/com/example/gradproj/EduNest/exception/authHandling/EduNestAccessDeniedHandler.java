package com.example.gradproj.EduNest.exception.authHandling;

import com.example.gradproj.EduNest.exception.ApiErrorCode;
import com.example.gradproj.EduNest.exception.ServletErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * P1-05 / EB-12 — unified 403 envelope. The underlying exception message is
 * NOT echoed; a clean, stable text is sent instead (the FE renders it
 * directly). Same shape/code as the advice-level AccessDeniedException
 * handler (ServletErrorWriter).
 */
@Component
public class EduNestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ServletErrorWriter.write(response,
                HttpStatus.FORBIDDEN.value(),
                ApiErrorCode.ERR_FORBIDDEN,
                "You do not have permission to access this resource.",
                request.getRequestURI());
    }
}
