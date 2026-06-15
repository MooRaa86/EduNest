package com.example.gradproj.EduNest.dto.mentorShipDTOs.request;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class mentorShipUpdateDTO {

    @Size(min = 3, max = 50, message = "Title must be between 3 and 50 characters")
    private String title;

    @Size(min = 3, max = 100, message = "Subtitle must be between 3 and 100 characters")
    private String subtitle;

    @Size(min = 3, max = 2000, message = "Description must be between 3 and 2000 characters")
    private String description;

    @Size(min = 3, max = 30, message = "Category must be between 3 and 30 characters")
    private String category;

    private DifficultyLevel difficultyLevel;

    @PositiveOrZero
    private Double price;

    @Min(value = 0, message = "Discount must be between 0 and 100")
    @Max(value = 100, message = "Discount must be between 0 and 100")
    private Integer discountPercentage;

    @Size(max = 12, message = "Maximum 12 items allowed")
    private List<
            @NotBlank
            @Size(min = 3, max = 200,message = "What will learn must be between 3 and 200 characters")
                    String
            > whatWillLearn;

    @Size(max = 10)
    private List<
            @NotBlank
            @Size(min = 2, max = 20 ,message = "Tag must be between 2 and 20 characters")
                    String
            > tags;

    @Positive
    private Double duration;
}
