package com.example.gradproj.EduNest.dto.lectures;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LectureResponse {
    private UUID id;
    private String title;
    private String lectureUrl;
}
