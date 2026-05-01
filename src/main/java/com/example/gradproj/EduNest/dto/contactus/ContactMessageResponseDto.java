package com.example.gradproj.EduNest.dto.contactus;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContactMessageResponseDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String message;
}
