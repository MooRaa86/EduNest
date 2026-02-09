package com.example.gradproj.EduNest.dto.livesession.request;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class UpdateSessionDto {
    private String title;
    private LocalDate date;
    private LocalTime time;
}
