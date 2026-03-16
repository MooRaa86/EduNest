package com.example.gradproj.EduNest.dto.badges.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AwardBadgeRequest {

    @NotNull
    private Long studentId;

    private String note;
}
