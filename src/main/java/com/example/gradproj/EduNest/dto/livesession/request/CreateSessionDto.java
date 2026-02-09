package com.example.gradproj.EduNest.dto.livesession.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class CreateSessionDto {

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotNull(message = "Date cannot be null")
    private LocalDate date;

    @NotNull(message = "Time cannot be null")
    private LocalTime time;

    @NotNull(message = "Mentorship ID cannot be null")
    private Long mentorshipId;
}

