package com.example.gradproj.EduNest.dto.mentorShipDTOs.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class CreateReviewRequest {

    @Min(1)
    @Max(5)
    private int rating;

    @Length(max = 500, message = "Feedback must be less than 500 characters")
    private String feedback;
}
