package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;
import java.util.UUID;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentWeekContentsResponse {
    private UUID weekId;
    private String weekTitle;
    private List<StudentWeekContentItemDTO> items;
}
