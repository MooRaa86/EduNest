package com.example.gradproj.EduNest.service.certificate;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private final MentorShipRepository mentorShipRepository;
    private final CertificateRepository certificateRepository;
    private final SecurityService securityService;
    private final CertificateIssuanceStore issuanceStore;

    @Transactional
    public void issueCertificates(Long mentorshipId) {
        log.info("Start issuing certificates for mentorship {}", mentorshipId);

        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnMentorship(mentorshipId, email)) {
            throw new AccessDeniedException("You are not authorized to issue certificates for this mentorship");
        }

        var mentorship = mentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        issuanceStore.issue(mentorshipId, mentorship.getTitle(), email);
    }

    public PageResponse<CertificateResponse> getStudentCertificates(String email, int page, int size) {
        var result = certificateRepository.findByStudentEmail(email, PageRequest.of(page, size));

        return PageResponse.<CertificateResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }
}
