package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MentorProfileMentorshipsDto {
    private Long id;
    private String MentorshipTitle;
    private String MentorshipSubtitle;
    private String Category;
    private DifficultyLevel difficultyLevel;
    private Double price;
    private Integer discountPercentage;
//    private Double finalPrice;
    private double duration;
    private String coverImageUrl;
}
