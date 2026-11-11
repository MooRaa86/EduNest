package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WeekResponse {
    private UUID id;
    private String title;
}
