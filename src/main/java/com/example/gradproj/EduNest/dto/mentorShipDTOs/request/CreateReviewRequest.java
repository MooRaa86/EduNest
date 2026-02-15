package com.example.gradproj.EduNest.dto.mentorShipDTOs.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReviewRequest {

    @Min(1)
    @Max(5)
    private int rating;

    private String feedback;
}
