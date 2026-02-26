package com.example.gradproj.EduNest.dto.profile;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FullProfileStudentInformationForMentorResponse {
    private ProfileStudentInformationForMentorResponse profileStudentInformationForMentorResponse;
    private  PageResponse<EnrolledMentorshipProgressDto> enrolledMentorshipProgressDtoPageResponse;
    private PageResponse<StudentProjectProfileDTO> projectProfileDTOPageResponse;
}
