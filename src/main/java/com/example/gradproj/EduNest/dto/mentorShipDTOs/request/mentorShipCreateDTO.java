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

    @NotBlank(message = "SubTitle is required")
    @Size(min = 3, max = 100, message = "Subtitle must be between 3 and 100 characters")
    private String subtitle;

    @NotBlank(message = "Description is required")
    @Size(min = 3, max = 2000, message = "Description must be between 3 and 2000 characters")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(min = 3, max = 30, message = "Category must be between 3 and 30 characters")
    private String category; // enum بعدين

    private DifficultyLevel difficultyLevel;

    @PositiveOrZero(message = "Price must be zero or positive")
    private double price;

    @Min(value = 0, message = "Discount must be between 0 and 100")
    @Max(value = 100, message = "Discount must be between 0 and 100")
    @Builder.Default
    private Integer discountPercentage = 0;

    @NotEmpty(message = "What will learn list cannot be empty")
    @Size(max = 12, message = "Maximum 12 items allowed")
    private List<
            @NotBlank
            @Size(min = 2, max = 100)
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
