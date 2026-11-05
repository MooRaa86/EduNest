package com.example.gradproj.EduNest.config.logging;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

/** Retain bounded diagnostic frames without emitting exception messages or causes' data. */
public final class SafeThrowableConverter extends ThrowableHandlingConverter {
    @Override
    public String convert(ILoggingEvent event) {
        IThrowableProxy cause = event.getThrowableProxy();
        if (cause == null) return "";
        var out = new StringBuilder();
        int depth = 0;
        while (cause != null && depth++ < 8) {
            if (out.length() > 0) out.append(" <- ");
            out.append(cause.getClassName());
            StackTraceElementProxy[] frames = cause.getStackTraceElementProxyArray();
            if (frames != null) {
                for (int i = 0; i < Math.min(frames.length, 30); i++) {
                    out.append(" | ").append(frames[i].getStackTraceElement());
                }
                if (frames.length > 30) out.append(" | ...");
            }
            cause = cause.getCause();
        }
        if (cause != null) out.append(" <- ...");
        return " exception=\"" + SafeMessageConverter.escape(out.toString()) + "\"";
    }
}
