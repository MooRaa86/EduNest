package com.example.gradproj.EduNest.exception;

import lombok.Builder;

import java.util.HashMap;
import java.util.Map;

@Builder
public class Response {
    Map<String,String> Body=new HashMap<>();

    public void addResponse(String field, String message){
        Body.put(field,message);
    }
}
