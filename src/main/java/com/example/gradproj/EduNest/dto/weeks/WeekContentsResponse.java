package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeekContentsResponse {
    private Long weekId;
    private String weekTitle;
    private List<WeekContentItemDTO> items;
}
