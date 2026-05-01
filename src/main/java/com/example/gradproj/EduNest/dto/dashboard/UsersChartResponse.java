package com.example.gradproj.EduNest.dto.dashboard;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UsersChartResponse {
        private String month;
        private Integer year;
        private Long totalUsers;
}
