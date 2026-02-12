package com.example.gradproj.EduNest.dto.weeks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWeekrequest {
   @NotNull
   private Long mentorshipId;

   @NotBlank(message = "title is required")
   @Size(min = 3, max = 150, message = "title must be between 3 and 150 characters")
   private String title;
}
