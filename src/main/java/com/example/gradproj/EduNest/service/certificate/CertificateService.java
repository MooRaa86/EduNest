package com.example.gradproj.EduNest.service.certificate;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private final TotalPointsRepository totalPointsRepository;
    private final MentorShipRepository mentorShipRepository;
    private final CertificateRepository certificateRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;
    private final SecurityService securityService;

    @Transactional
    public void issueCertificates(Long mentorshipId) {
        log.info("Start issuing certificates for mentorship {}", mentorshipId);

        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnMentorship(mentorshipId, email)) {
            throw new AccessDeniedException("You are not authorized to issue certificates for this mentorship");
        }

        var mentorship = mentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        var leaderboard = totalPointsRepository
                .findLeaderboardByMentorshipId(mentorshipId, Pageable.unpaged())
                .getContent();

        if (leaderboard.isEmpty()) {
            log.info("No students found in leaderboard for mentorship {}", mentorshipId);
            return;
        }
        List<String> emails = leaderboard.stream()
                .map(row -> row.getStudentEmail())
                .toList();
        Map<String, Student> studentsByEmail = studentRepository.findAllByEmailIn(emails).stream()
                .collect(Collectors.toMap(Student::getEmail, Function.identity()));
        LocalDateTime now = LocalDateTime.now();

        List<Certificate> toSave = new ArrayList<>();
        for (int i = 0; i < leaderboard.size(); i++) {
            var row = leaderboard.get(i);
            Student student = studentsByEmail.get(row.getStudentEmail());
            if (student == null) {
                log.warn("Student with email {} not found while issuing certificates", row.getStudentEmail());
                continue;
            }
            toSave.add(Certificate.builder()
                    .student(student)
                    .mentorship(mentorship)
                    .rank(i + 1)
                    .issuedAt(now)
                    .build());
        }

        certificateRepository.saveAll(toSave);
        log.info("Issued {} certificates for mentorship {}", toSave.size(), mentorshipId);

        // Notify each student about their certificate
        for (Certificate cert : toSave) {
            notificationService.sendToUserByEmail(
                    cert.getStudent().getEmail(),
                    "Certificate Issued!",
                    "Congratulations! You earned a certificate in \"" + mentorship.getTitle() + "\" with rank #" + cert.getRank(),
                    NotificationType.CERTIFICATE
            );
        }
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