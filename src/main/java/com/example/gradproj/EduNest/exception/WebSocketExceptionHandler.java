package com.example.gradproj.EduNest.exception;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public String handleException(Exception ex) {
        return ex.getMessage();
    }
}
