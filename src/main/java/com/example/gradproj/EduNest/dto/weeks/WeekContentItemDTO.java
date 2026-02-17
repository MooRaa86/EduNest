package com.example.gradproj.EduNest.dto.weeks;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeekContentItemDTO {
    private String type;     // SESSION / QUIZ / TASK / PROJECT
    private Long id;
    private String title;
//    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
