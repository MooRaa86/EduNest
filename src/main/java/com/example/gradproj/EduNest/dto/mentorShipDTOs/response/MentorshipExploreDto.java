package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorshipExploreDto {
    private UUID id;
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
