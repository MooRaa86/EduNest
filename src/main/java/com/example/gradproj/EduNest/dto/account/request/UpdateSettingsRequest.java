package com.example.gradproj.EduNest.dto.account.request;

import com.example.gradproj.EduNest.enums.account.ThemeMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSettingsRequest {
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean weeklyDigest;
    private Boolean messageNotifications;
    private ThemeMode mode;
}