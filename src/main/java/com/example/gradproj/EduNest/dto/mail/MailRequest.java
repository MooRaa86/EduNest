package com.example.gradproj.EduNest.dto.mail;

import lombok.*;

@Setter @Getter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class MailRequest {

    private String email;
    private String message;
    private String name;

}