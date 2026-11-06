package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfileforStudentDto {

    /**
     * Opaque UUID public identifier for the mentor (EB-109 / D-04).
     */
    private UUID mentorId;

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
     * Alias getter so frontend can consume either mentorId or publicId.
     */
    @JsonProperty("publicId")
    public UUID getPublicId() {
        return mentorId;
    }

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
