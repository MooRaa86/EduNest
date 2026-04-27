package com.example.gradproj.EduNest.dto.auth;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAccResponse {
    private String email;
    private String firstName;
    private String lastName;
    private String password;
}
