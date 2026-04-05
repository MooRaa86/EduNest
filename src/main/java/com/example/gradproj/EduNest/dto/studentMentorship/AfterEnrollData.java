package com.example.gradproj.EduNest.dto.studentMentorship;

import com.example.gradproj.EduNest.dto.homepage.UpcomingItemDto;
import com.example.gradproj.EduNest.dto.homepage.studentProgressDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AfterEnrollData {
    private Long id;
    private String title;
    private String subtitle;
    private String description;
    private String category;
    private String coverImageUrl;
    private List<String> whatWillLearn;
    private List<String> tags;
    private String mentorName;
    private String mentorEmail;
    private String mentorProfileImageUrl;
    private String mentorJobTitle;
    private Integer mentorYearsOfExperience;
    private studentProgressDto progress;
    private PageResponse<UpcomingItemDto> upcomingItems;
}
