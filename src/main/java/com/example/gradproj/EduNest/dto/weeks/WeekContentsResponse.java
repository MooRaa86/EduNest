package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;
import java.util.UUID;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeekContentsResponse {
    private UUID weekId;
    private String weekTitle;
    private List<WeekContentItemDTO> items;
}
