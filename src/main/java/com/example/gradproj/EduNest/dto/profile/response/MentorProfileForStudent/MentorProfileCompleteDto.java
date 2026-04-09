package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MentorProfileCompleteDto {
    private MentorProfileforStudentDto mentorProfile;
    private PageResponse<MentorProfileMentorshipsDto> mentorships;
    private PageResponse<MentorProfileReviews> reviews;
}
