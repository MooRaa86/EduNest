package com.example.gradproj.EduNest.service.certificate;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final TotalPointsRepository totalPointsRepository;
    private final MentorShipRepository mentorShipRepository;
    private final CertificateRepository certificateRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public void issueCertificates(Long mentorshipId) {
        var mentorship = mentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        var students = totalPointsRepository
                .findLeaderboardByMentorshipId(mentorshipId, Pageable.unpaged())
                .getContent();

        Set<Long> alreadyIssued = certificateRepository.findStudentIdsByMentorshipId(mentorshipId);

        LocalDateTime now = LocalDateTime.now();
        List<Certificate> toSave = new ArrayList<>();

        for (int i = 0; i < students.size(); i++) {
            var s = students.get(i);
            var student = studentRepository.findByEmail(s.getStudentEmail())
                    .orElseThrow(() -> new globalLogicEx("Student not found"));

            if (alreadyIssued.contains(student.getId())) continue;

            toSave.add(Certificate.builder()
                    .student(student)
                    .mentorship(mentorship)
                    .rank(i + 1)
                    .issuedAt(now)
                    .build());
        }

        certificateRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> getStudentCertificates() {
        String email = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));

        return certificateRepository.findByStudentEmail(email);
    }
}
