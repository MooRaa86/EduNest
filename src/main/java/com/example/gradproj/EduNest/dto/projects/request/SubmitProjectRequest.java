package com.example.gradproj.EduNest.dto.projects.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.URL;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SubmitProjectRequest {
    @NotBlank(message = "fileUrl is required")
    @Size(max = 500, message = "fileUrl max length is 500")
    @URL(message = "fileUrl must be a valid URL")
    private String fileUrl;
}
