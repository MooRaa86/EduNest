package com.example.gradproj.EduNest.config.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.gradproj.EduNest.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionLogPrivacyTest {
    @Test
    void databaseErrorValuesAreNotLoggedOrReturned() {
        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String sentinel = "sensitive-submitted-value";
            var response = new GlobalExceptionHandler().handleDataIntegrityViolation(
                    new MockHttpServletRequest("POST", "/probe"), new DataIntegrityViolationException(sentinel));
            assertNotNull(response.getBody());
            assertFalse(response.getBody().getMessage().contains(sentinel));
            assertFalse(appender.list.isEmpty());
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(sentinel)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
