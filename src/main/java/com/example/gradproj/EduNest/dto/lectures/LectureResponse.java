package com.example.gradproj.EduNest.dto.lectures;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LectureResponse {
    private Long id;
    private String title;
    private String lectureUrl;
}
