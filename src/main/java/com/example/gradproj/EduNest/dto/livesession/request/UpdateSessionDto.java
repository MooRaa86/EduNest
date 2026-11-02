package com.example.gradproj.EduNest.dto.livesession.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionDto {
    private String title;
    private LocalDateTime scheduledAt;
}
