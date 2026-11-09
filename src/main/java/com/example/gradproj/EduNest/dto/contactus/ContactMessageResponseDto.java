package com.example.gradproj.EduNest.dto.contactus;

import com.example.gradproj.EduNest.enums.message.MessageStatus;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContactMessageResponseDto {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String message;
    private MessageStatus status;
}
