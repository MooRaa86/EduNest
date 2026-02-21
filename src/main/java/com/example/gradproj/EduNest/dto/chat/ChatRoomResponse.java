package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter @Getter @AllArgsConstructor
public class ChatRoomResponse {
    private Long id;
    private String name;
    private Long mentorshipId;
}
