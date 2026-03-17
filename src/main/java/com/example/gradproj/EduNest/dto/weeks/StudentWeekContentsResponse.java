package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentWeekContentsResponse {
    private Long weekId;
    private String weekTitle;
    private List<StudentWeekContentItemDTO> items;
}
