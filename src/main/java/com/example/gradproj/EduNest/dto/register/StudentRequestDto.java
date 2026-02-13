package com.example.gradproj.EduNest.dto.register;

import com.example.gradproj.EduNest.enums.register.EducationalLevel;
import lombok.*;

@Builder @AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class StudentRequestDto extends RegisterRequestDto {

    private EducationalLevel educationalLevel;
}
