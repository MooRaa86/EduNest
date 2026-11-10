package com.example.gradproj.EduNest.dto.livesession.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionDto {

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotNull(message = "Scheduled date/time cannot be null")
    private LocalDateTime scheduledAt;

    @NotNull(message = "week ID cannot be null")
    private UUID weekId;
}
