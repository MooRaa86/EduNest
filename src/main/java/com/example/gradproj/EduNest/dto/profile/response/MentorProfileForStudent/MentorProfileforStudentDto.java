package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfileforStudentDto {

    /**
     * @deprecated Temporary numeric identifier bridge (EB-88).
     * Will transition to publicId UUID in EB-109.
     */
    @Deprecated
    private Long mentorId;

    private String profileImageUrl;
    private String mentorFirstName;
    private String mentorLastName;
    private double mentorYearsOfExperience;
    private Long totalLearners;
    private Long totalReviews;
    private Double avgReviewRate;
    private String bio;

    /**
     * @deprecated Exposing mentor email on public profiles violates D-04 / USR-02.
     * Retained for transitional backwards compatibility (redacted/nullable).
     */
    @Deprecated
    private String mentorEmail;

    private List<SocialMediaLinksDto> socialMediaLinks;

    /**
     * Legacy 10-arg constructor for JPQL backwards compatibility.
     */
    public MentorProfileforStudentDto(
            String profileImageUrl,
            String mentorFirstName,
            String mentorLastName,
            double mentorYearsOfExperience,
            Long totalLearners,
            Long totalReviews,
            Double avgReviewRate,
            String bio,
            String mentorEmail,
            List<SocialMediaLinksDto> socialMediaLinks
    ) {
        this(null, profileImageUrl, mentorFirstName, mentorLastName, mentorYearsOfExperience,
                totalLearners, totalReviews, avgReviewRate, bio, mentorEmail, socialMediaLinks);
    }
}
