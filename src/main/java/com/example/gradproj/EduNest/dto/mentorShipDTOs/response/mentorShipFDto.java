package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;
import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class mentorShipFDto {

    @NotNull
    private Long id;
    private Status status;
    private String title;
    private String description;
    private String category;
    private Double rating;
    private DifficultyLevel difficultyLevel;
    private double price;
    private List<String> whatWillLearn;
    private List<String> tags;
    private Double duration;
    private String coverImageUrl;
}
