package com.example.gradproj.EduNest.dto.studentMentorship;

import com.example.gradproj.EduNest.dto.homepage.UpcomingItemDto;
import com.example.gradproj.EduNest.dto.homepage.studentProgressDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AfterEnrollData {
    private UUID id;
    private UUID publicId;
    private String title;
    private String subtitle;
    private String description;
    private String category;
    private String coverImageUrl;
    private List<String> whatWillLearn;
    private List<String> tags;

    /**
     * Opaque UUID public identifier for the mentor (EB-109 / D-04).
     */
    private UUID mentorId;

    private String mentorName;

    /**
     * @deprecated Exposing mentor email violates D-04 / USR-02.
     * Retained for transitional backwards compatibility.
     */
    @Deprecated
    private String mentorEmail;

    private String mentorProfileImageUrl;
    private String mentorJobTitle;
    private Integer mentorYearsOfExperience;
    private studentProgressDto progress;
    private PageResponse<UpcomingItemDto> upcomingItems;
}
