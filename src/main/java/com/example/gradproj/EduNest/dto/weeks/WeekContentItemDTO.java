package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeekContentItemDTO {
    private String type;     // SESSION / QUIZ / TASK / PROJECT
    private Long id;
    private String title;
    private String status;
}
