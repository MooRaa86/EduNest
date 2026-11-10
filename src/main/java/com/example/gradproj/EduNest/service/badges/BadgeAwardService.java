package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.dto.badges.request.AwardBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.response.BadgeAwardResponse;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BadgeAwardService {

    private final BadgeAwardRepository badgeAwardRepository;
    private final BadgeRepository badgeRepository;
    private final StudentRepository studentRepository;
    private final MentorRepository mentorRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final MentorShipRepository mentorShipRepository;
    private final NotificationService notificationService;
    private final EntityManager entityManager;

    private String getCurrentUserEmail() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    private Mentor getCurrentMentor() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"));
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public BadgeAwardResponse awardBadge(Long badgeId, AwardBadgeRequest req) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        Mentor mentor = getCurrentMentor();

        if (!badge.getMentorship().getMentor().getId().equals(mentor.getId()))
            throw new AccessDeniedException("You are not authorized to award badges for this mentorship");

        // EB-51 — L0 shared gate taken before any eligibility check or ledger
        // mutation: badge point edits and the COMPLETED transition are
        // serialised against this award so the CREDITED points snapshot
        // (badge.getPoints()) is stable while applyDelta runs.
        MentorShip l0Gate = mentorShipRepository.findForUpdateById(badge.getMentorship().getId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        // EB-51 / D-09 (CERT-04) — post-completion badge freeze: cannot award badge once mentorship is COMPLETED
        if (l0Gate.getStatus() == Status.COMPLETED) {
            throw new globalLogicEx("Cannot award badge once the mentorship is completed");
        }

        // EB-51 — acquire locked badge from repository under the L0 gate
        badge = badgeRepository.findForUpdateById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        Student student = studentRepository.findByPublicId(req.getStudentId())
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Id(badge.getMentorship().getId(), student.getId()))
            throw new globalLogicEx("Student is not enrolled in the mentorship that owns this badge");

        if (badgeAwardRepository.existsByBadge_IdAndStudent_Id(badgeId, student.getId()))
            throw new globalLogicEx("Student has already been awarded this badge");

        BadgeAward award = BadgeAward.builder()
                .badge(badge)
                .student(student)
                .awardedBy(mentor)
                .note(req.getNote())
                .build();

        badgeAwardRepository.save(award);

        totalPointsService.applyDelta(student, l0Gate, badge.getPoints());

        // Notify the student that they received a badge
        notificationService.sendToUserByEmail(
                student.getEmail(),
                "Badge Awarded! ",
                "Congratulations! You earned the badge \"" + badge.getTitle() + "\" in mentorship \"" + badge.getMentorship().getTitle() + "\". Points: +" + badge.getPoints(),
                NotificationType.BADGE
        );

        return toDto(award);
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardResponse> getAwardsByMentorship(Long mentorshipId) {
        MentorShip mentorship = mentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        if (!mentorship.getMentor().getId().equals(getCurrentMentor().getId()))
            throw new AccessDeniedException("You are not authorized to view awards for this mentorship");

        return badgeAwardRepository.findByBadge_Mentorship_Id(mentorshipId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardResponse> getAwardsByStudent(Long studentId) {
        // P1-19 / EB-26 (G11 / GAME-02) — award lists carry personal data
        // (who earned what, notes); badge awards were enumerable by
        // incrementing ids. Scoped to: ADMIN, the student themself (email
        // match on the principal-derived caller, D-03), or a MENTOR of one of
        // the student's enrollments (matches the mentor student-profile
        // surface). Everyone else -> 403; anonymous -> 401 (chain).
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerEmail = getCurrentUserEmail();

        boolean admin = hasRole(authentication, "ADMIN");
        boolean self = studentId != null && studentRepository.findById(studentId)
                .map(s -> callerEmail.equals(s.getEmail())).orElse(false);
        boolean mentorOfEnrollment = studentId != null && hasRole(authentication, "MENTOR")
                && enrollmentRepository.existsByStudent_IdAndMentorShip_Mentor_Email(studentId, callerEmail);

        if (!admin && !self && !mentorOfEnrollment)
            throw new AccessDeniedException("You are not authorized to view this student's badge awards");

        return badgeAwardRepository.findByStudent_IdOrderByCreatedAtDesc(studentId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardResponse> getAwardsByBadge(Long badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        if (!badge.getMentorship().getMentor().getId().equals(getCurrentMentor().getId()))
            throw new AccessDeniedException("You are not authorized to view awards for this badge");

        return badgeAwardRepository.findByBadge_Id(badgeId)
                .stream().map(this::toDto).toList();
    }

    private BadgeAwardResponse toDto(BadgeAward a) {
        return BadgeAwardResponse.builder()
                .id(a.getPublicId())
                .badgeId(a.getBadge().getPublicId())
                .badgeTitle(a.getBadge().getTitle())
                .studentId(a.getStudent().getPublicId())
                .studentFullName(a.getStudent().getFirstName() + " " + a.getStudent().getLastName())
                .awardedById(a.getAwardedBy().getPublicId())
                .awardedAt(a.getCreatedAt())
                .note(a.getNote())
                .badgePoints(a.getBadge().getPoints())
                .build();
    }

    private Long resolveBadgeInternalId(UUID publicId) {
        return badgeRepository.findIdByPublicId(publicId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));
    }

    private Long resolveMentorshipInternalId(UUID publicId) {
        return mentorShipRepository.findIdByPublicId(publicId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));
    }

    private Long resolveStudentInternalId(UUID publicId) {
        return studentRepository.findIdByPublicId(publicId)
                .orElseThrow(() -> new globalLogicEx("Student not found"));
    }

    public BadgeAwardResponse awardBadge(UUID badgeId, AwardBadgeRequest req) {
        return awardBadge(resolveBadgeInternalId(badgeId), req);
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardResponse> getAwardsByMentorship(UUID mentorshipId) {
        return getAwardsByMentorship(resolveMentorshipInternalId(mentorshipId));
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardResponse> getAwardsByStudent(UUID studentId) {
        Long internalId = studentRepository.findIdByPublicId(studentId).orElse(-1L);
        return getAwardsByStudent(internalId);
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardResponse> getAwardsByBadge(UUID badgeId) {
        return getAwardsByBadge(resolveBadgeInternalId(badgeId));
    }

}
