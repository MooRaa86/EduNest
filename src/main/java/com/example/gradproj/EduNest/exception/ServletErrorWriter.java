package com.example.gradproj.EduNest.exception;

import com.example.gradproj.EduNest.exception.ApiErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * P1-05 / EB-12 — single servlet-layer serializer for the unified
 * {@link ApiError} envelope. Guarantees that the security entry point,
 * access-denied handler, JWT filter, and any error controller emit the
 * EXACT same shape (one writer, no drift).
 */
public final class ServletErrorWriter {

    private ServletErrorWriter() {
    }

    public static void write(HttpServletResponse response,
                             int status,
                             ApiErrorCode code,
                             String message,
                             String path) throws IOException {
        write(response, status, code, message, path, null);
    }

    /**
     * EB-15: throttling/lockout rejections carry Retry-After (seconds) — it
     * MUST be flushed before the body, hence the dedicated overload.
     */
    public static void write(HttpServletResponse response,
                             int status,
                             ApiErrorCode code,
                             String message,
                             String path,
                             java.util.Map<String, String> extraHeaders) throws IOException {
        if (extraHeaders != null) {
            extraHeaders.forEach(response::setHeader);
        }
        ApiError body = ApiError.of(status, statusReason(status), code, message, path);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(toJson(body));
    }

    static String statusReason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }

    /**
     * Hand-rolled serializer so the servlet writers don't need an injected
     * ObjectMapper; JSON-escapes the mutable text pieces. All of the values
     * are handler-authored constants (no internal exception text), but a
     * path could echo odd characters — escape strictly.
     */
    static String toJson(ApiError apiError) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"timestamp\":\"").append(apiError.getTimestamp()).append('"');
        sb.append(",\"status\":").append(apiError.getStatus());
        sb.append(",\"error\":\"").append(esc(apiError.getError())).append('"');
        sb.append(",\"errorCode\":\"").append(esc(apiError.getErrorCode())).append('"');
        sb.append(",\"message\":\"").append(esc(apiError.getMessage())).append('"');
        sb.append(",\"path\":\"").append(esc(apiError.getPath())).append('"');
        if (apiError.getRefId() != null) {
            sb.append(",\"refId\":\"").append(esc(apiError.getRefId())).append('"');
        }
        if (apiError.getFields() != null && !apiError.getFields().isEmpty()) {
            sb.append(",\"fields\":{");
            boolean first = true;
            for (var e : apiError.getFields().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append('"');
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
