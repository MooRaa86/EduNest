package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorshipExploreDto {
    private Long id;
    private String title;
    private String subtitle;
    private String description;
    private String category;
    private String mentorName;
    private Double price;
    private Integer discountPercentage;
    private Double priceAfterDiscount;
    private Double duration;
    private String coverImageUrl;
    private Double rating;
}
