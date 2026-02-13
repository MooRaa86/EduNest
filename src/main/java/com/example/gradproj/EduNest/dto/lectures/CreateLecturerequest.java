package com.example.gradproj.EduNest.dto.lectures;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.URL;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateLecturerequest {
    @NotNull
    private Long weekId;

    @NotBlank(message = "title is required")
    @Size(min = 3, max = 150, message = "title must be between 3 and 150 characters")
    private String title;

    @Size(max = 500, message = "Lecture Url max length is 500")
    @URL(message = "Lecture Url must be a valid URL")
    private String lectureUrl;

}
