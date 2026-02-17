package com.example.gradproj.EduNest.dto.mentorShipDTOs.request;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class mentorShipCreateDTO {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 50, message = "Title must be between 3 and 50 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 3, max = 500, message = "Description must be between 3 and 500 characters")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(min = 3, max = 50, message = "Category must be between 3 and 20 characters")
    private String category; // enum بعدين

    private DifficultyLevel difficultyLevel;

    @PositiveOrZero(message = "Price must be zero or positive")
    private double price;

    @NotEmpty(message = "What will learn list cannot be empty")
    @Size(max = 12, message = "Maximum 12 items allowed")
    private List<
            @NotBlank
            @Size(min = 3, max = 100)
                    String
            > whatWillLearn = new ArrayList<>();

    @Size(max = 10, message = "Maximum 10 tags allowed")
    private List<
            @NotBlank(message = "tag connot be null")
            @Size(min = 2, max = 20)
                    String
            > tags = new ArrayList<>();

    @NotNull
    @Positive
    private Double duration;

}
