package com.example.gradproj.EduNest.dto.weeks;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateWeekRequest {
    @NotNull(message = "Title is required")
    private String title;
}
