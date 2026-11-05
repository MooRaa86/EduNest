package com.example.gradproj.EduNest.config.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Escape untrusted characters so messages cannot inject additional log records. */
public final class SafeMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) { return escape(event.getFormattedMessage()); }

    static String escape(String value) {
        if (value == null) return "";
        var out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
