package com.example.gradproj.EduNest.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ErrorResponse {
    Map<String,String> errorMessages=new HashMap<>();

    public void addError(String field, String message){
        errorMessages.put(field,message);
    }
}
