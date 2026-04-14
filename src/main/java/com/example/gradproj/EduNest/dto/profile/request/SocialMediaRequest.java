package com.example.gradproj.EduNest.dto.profile.request;

import com.example.gradproj.EduNest.enums.socialMedia.Media;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SocialMediaRequest {
    private Media name;
    private String url;
}
