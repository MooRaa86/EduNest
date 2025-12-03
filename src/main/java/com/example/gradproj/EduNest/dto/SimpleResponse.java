package com.example.gradproj.EduNest.dto;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class SimpleResponse {
    Map<String,String> Messages=new HashMap<>();

    public void addMessage(String key,String value){
        Messages.put(key,value);
    }
}
