package com.example.gradproj.EduNest.dto.certificate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateResponse {
    private String studentFullName;
    private String mentorFullName;
    private String mentorshipTitle;
    private String mentorshipSubtitle;
    private LocalDateTime issuedAt;
    private long rank;
}
