package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class ChatRoomResponse {
    private UUID id;
    private String name;
    private UUID mentorshipId;
}
