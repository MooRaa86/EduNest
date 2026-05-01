package com.example.gradproj.EduNest.dto.profile.response;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminProfileInformationResponse {
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String headline;
    private String profileImageUrl;
    private Double commissionRate;

}
