package com.example.gradproj.EduNest.exception;

import java.util.HashMap;
import java.util.Map;

public class Response {
    Map<String,String> Body=new HashMap<>();

    public void addResponse(String field, String message){
        Body.put(field,message);
    }
}
