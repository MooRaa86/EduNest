package com.example.gradproj.EduNest.dto.profile.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateAdminProfileRequest {
    private String firstName;
    private String lastName;
    private String headline;
}