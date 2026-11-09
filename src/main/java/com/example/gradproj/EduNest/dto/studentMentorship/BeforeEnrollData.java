package com.example.gradproj.EduNest.dto.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeforeEnrollData {
    private UUID id;
    private UUID publicId;
    private String title;
    private String subtitle;
    private String description;
    private String category;
    private DifficultyLevel difficultyLevel;
    private double duration;
    private Double price;
    private Integer discountPercentage;
    private Double finalPrice;
    private String coverImageUrl;
    private Status status;
    private Double rating;

    /**
     * Opaque UUID public identifier for the mentor (EB-109 / D-04).
     */
    private UUID mentorId;

    private String mentorName;

    /**
     * @deprecated Exposing mentor email on public surfaces violates D-04 / USR-02.
     * Retained for transitional backwards compatibility.
     */
    @Deprecated
    private String mentorEmail;

    private String mentorProfileImageUrl;
    private String mentorJobTitle;
    private Integer mentorYearsOfExperience;
    private List<String> tags;
    private List<String> whatWillLearn;
    private List<MentorshipExploreDto> topMentorMentorships;
}
