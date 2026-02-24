package com.example.gradproj.EduNest.dto.account.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SettingsResponse {

    private String email;
    private boolean emailNotifications;
    private boolean pushNotifications;
    private boolean weeklyDigest;
    private boolean messageNotifications;
    private String mode;
}