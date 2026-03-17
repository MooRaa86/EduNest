package com.example.gradproj.EduNest.service.studentAchievement;

import com.example.gradproj.EduNest.dto.studentAchievement.BadgeAchievementResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.ProjectAchievementResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.StudentAchievementResponse;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StudentAchievementService {

    private final BadgeAwardRepository badgeAwardRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final StudentRepository studentRepository;

    private String getCurrentUserEmail() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    public StudentAchievementResponse getAchievements() {
        Long studentId = studentRepository.findIdByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        List<BadgeAchievementResponse> badges = badgeAwardRepository
                .findByStudent_IdOrderByCreatedAtDesc(studentId)
                .stream().map(this::toBadgeDto).toList();

        List<ProjectAchievementResponse> submissions = projectSubmissionRepository
                .findForStudentProfile(studentId, Pageable.unpaged())
                .stream().map(this::toProjectDto).toList();

        return StudentAchievementResponse.builder()
                .badges(badges)
                .projectSubmissions(submissions)
                .build();
    }

    private BadgeAchievementResponse toBadgeDto(BadgeAward a) {
        return BadgeAchievementResponse.builder()
                .id(a.getId())
                .title(a.getBadge().getTitle())
                .description(a.getBadge().getDescription())
                .points(a.getBadge().getPoints())
                .mentorshipId(a.getBadge().getMentorship().getId())
                .mentorshipTitle(a.getBadge().getMentorship().getTitle())
                .awardedByFullName(a.getAwardedBy().getFirstName() + " " + a.getAwardedBy().getLastName())
                .awardedAt(a.getCreatedAt())
                .build();
    }

    private ProjectAchievementResponse toProjectDto(ProjectSubmission s) {
        return ProjectAchievementResponse.builder()
                .id(s.getId())
                .projectTitle(s.getProject().getTitle())
                .mentorshipId(s.getProject().getWeek().getMentorship().getId())
                .mentorshipTitle(s.getProject().getWeek().getMentorship().getTitle())
                .submissionStatus(s.getStatus())
                .fileUrl(s.getFileUrl())
                .uploadedFilePath(s.getUploadedFilePath())
                .feedback(s.getFeedBack())
                .submittedAt(s.getSubmittedAt())
                .build();
    }
}
