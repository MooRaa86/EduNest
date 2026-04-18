package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import com.example.gradproj.EduNest.enums.socialMedia.Media;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SocialMediaLinksDto {
    private Media media;
    private String link;
}
