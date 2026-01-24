package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

import com.example.gradproj.EduNest.enums.DifficultyLevel;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class mentorShipFDto {

    @NotNull
    private Long id;
    private String title;
    private String description;
    private String category;
    private Integer rating;
    private DifficultyLevel difficultyLevel;
}
