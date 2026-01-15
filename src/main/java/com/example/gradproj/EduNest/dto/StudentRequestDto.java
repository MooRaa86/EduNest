package com.example.gradproj.EduNest.dto;

import com.example.gradproj.EduNest.enums.EducationalLevel;
import lombok.*;

@Builder @AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class StudentRequestDto extends RegisterRequestDto {

    private EducationalLevel educationalLevel;
}
