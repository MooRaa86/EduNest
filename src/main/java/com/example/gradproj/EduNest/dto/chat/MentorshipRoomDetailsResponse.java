package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class MentorshipRoomDetailsResponse {
    private Long roomId;
    private String roomName;
    private String roomCoverImage;
    private boolean joined;
}
