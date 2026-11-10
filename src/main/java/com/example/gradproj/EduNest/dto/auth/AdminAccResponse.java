package com.example.gradproj.EduNest.dto.auth;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAccResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
}
