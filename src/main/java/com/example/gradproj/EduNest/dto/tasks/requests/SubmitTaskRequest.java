package com.example.gradproj.EduNest.dto.tasks.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.URL;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SubmitTaskRequest {
    @NotNull(message = "studentId is required")
    @Positive(message = "studentId must be positive")
    private Long studentId;

    @NotBlank(message = "fileUrl is required")
    @Size(max = 500, message = "fileUrl max length is 500")
    @URL(message = "fileUrl must be a valid URL")
    private String fileUrl;
}
