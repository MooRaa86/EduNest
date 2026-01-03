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
    Map<String,Object> Messages=new HashMap<>();

    public void addMessage(String key,Object value){
        Messages.put(key,value);
    }
}
