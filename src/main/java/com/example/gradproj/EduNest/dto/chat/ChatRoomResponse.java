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

    public static class ChatRoomResponseBuilder {
        public ChatRoomResponseBuilder id(Long id) {
            this.id = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ChatRoomResponseBuilder id(UUID id) {
            this.id = id;
            return this;
        }
        public ChatRoomResponseBuilder mentorshipId(Long id) {
            this.mentorshipId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ChatRoomResponseBuilder mentorshipId(UUID id) {
            this.mentorshipId = id;
            return this;
        }
    }
}
