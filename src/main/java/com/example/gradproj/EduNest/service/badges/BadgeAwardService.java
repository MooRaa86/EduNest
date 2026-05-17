package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.dto.badges.request.AwardBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.response.BadgeAwardResponse;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    public BadgeAwardResponse awardBadge(Long badgeId, AwardBadgeRequest req) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        Mentor mentor = getCurrentMentor();

        if (!badge.getMentorship().getMentor().getId().equals(mentor.getId()))
            throw new AccessDeniedException("You are not authorized to award badges for this mentorship");

        Student student = studentRepository.findById(req.getStudentId())
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        if (!enrollmentRepository.existsByMentorShip_IdAndStudent_Id(badge.getMentorship().getId(), req.getStudentId()))
            throw new globalLogicEx("Student is not enrolled in the mentorship that owns this badge");

        if (badgeAwardRepository.existsByBadge_IdAndStudent_Id(badgeId, req.getStudentId()))
            throw new globalLogicEx("Student has already been awarded this badge");

        BadgeAward award = BadgeAward.builder()
                .badge(badge)
                .student(student)
                .awardedBy(mentor)
                .note(req.getNote())
                .build();

        badgeAwardRepository.save(award);

        totalPointsService.applyDelta(student, badge.getMentorship(), badge.getPoints());

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
                .id(a.getId())
                .badgeId(a.getBadge().getId())
                .badgeTitle(a.getBadge().getTitle())
                .studentId(a.getStudent().getId())
                .studentFullName(a.getStudent().getFirstName() + " " + a.getStudent().getLastName())
                .awardedById(a.getAwardedBy().getId())
                .awardedAt(a.getCreatedAt())
                .note(a.getNote())
                .badgePoints(a.getBadge().getPoints())
                .build();
    }
}
