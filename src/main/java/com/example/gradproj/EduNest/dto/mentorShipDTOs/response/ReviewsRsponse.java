package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewsRsponse {

    private String feedBack;
    double rating;
    private String mentorShip;
    private String studentName;
    private LocalDateTime reviewDate;

}
