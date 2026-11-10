package com.example.gradproj.EduNest.dto.badges.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardBadgeRequest {

    @NotNull
    private UUID studentId;

    private String note;
}
