package com.example.gradproj.EduNest.dto.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeforeEnrollData {
    private Long id;
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
     * @deprecated Temporary numeric identifier bridge (EB-88).
     * Will transition to publicId UUID in EB-109.
     */
    @Deprecated
    private Long mentorId;

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
