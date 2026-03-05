package com.example.gradproj.EduNest.dto.mentorShipDTOs.request;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class mentorShipUpdateDTO {

    @Size(min = 3, max = 50, message = "Title must be between 3 and 50 characters")
    private String title;

    @Size(min = 3, max = 100, message = "Subtitle must be between 3 and 100 characters")
    private String subtitle;

    @Size(min = 3, max = 500, message = "Description must be between 3 and 500 characters")
    private String description;

    @Size(min = 3, max = 20, message = "Category must be between 3 and 20 characters")
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
            @Size(min = 3, max = 100)
                    String
            > whatWillLearn;

    @Size(max = 10)
    private List<
            @NotBlank
            @Size(min = 2, max = 20)
                    String
            > tags;

    @Positive
    private Double duration;
}
