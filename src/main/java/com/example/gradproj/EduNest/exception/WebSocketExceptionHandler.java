package com.example.gradproj.EduNest.exception;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

    /**
     * P1-05 / EB-12 — the WS error queue follows the same zero-leak policy as
     * the REST envelope: authored business messages (globalLogicEx) pass
     * through; ANY unexpected exception is sanitized to the generic system
     * text (previously the raw ex.getMessage() — including internals — was
     * forwarded to the client). Note: as a plain string frame, no
     * errorCode/refId here — clients of /queue/errors treat any non-business
     * text as "generic failure" (documented in mds/api/error-responses.md).
     */
    @MessageExceptionHandler(globalLogicEx.class)
    @SendToUser("/queue/errors")
    public String handleBusinessException(globalLogicEx ex) {
        return ex.getMessage();
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public String handleException(Exception ex) {
        return "An unexpected system error occurred. Please try again.";
    }
}
