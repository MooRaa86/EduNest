package com.example.gradproj.EduNest.service.studentAchievement;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.BadgeAchievementResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.ProjectAchievementResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.StudentAchievementResponse;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public StudentAchievementResponse getAchievements(int badgesPage, int badgesSize, int projectsPage, int projectsSize) {
        Long studentId = studentRepository.findIdByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        var badgesPageResult = badgeAwardRepository
                .findByStudent_IdOrderByCreatedAtDesc(studentId, PageRequest.of(badgesPage, badgesSize));

        PageResponse<BadgeAchievementResponse> badges = PageResponse.<BadgeAchievementResponse>builder()
                .content(badgesPageResult.getContent().stream().map(this::toBadgeDto).toList())
                .page(badgesPageResult.getNumber())
                .size(badgesPageResult.getSize())
                .totalElements(badgesPageResult.getTotalElements())
                .totalPages(badgesPageResult.getTotalPages())
                .build();

        var submissionsPage = projectSubmissionRepository
                .findForStudentProfile(studentId, SubmissionStatus.GRADED, PageRequest.of(projectsPage, projectsSize));

        PageResponse<ProjectAchievementResponse> submissions = PageResponse.<ProjectAchievementResponse>builder()
                .content(submissionsPage.getContent().stream().map(this::toProjectDto).toList())
                .page(submissionsPage.getNumber())
                .size(submissionsPage.getSize())
                .totalElements(submissionsPage.getTotalElements())
                .totalPages(submissionsPage.getTotalPages())
                .build();

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
                .mentorFullName(s.getProject().getWeek().getMentorship().getMentor().getFirstName() + " " + s.getProject().getWeek().getMentorship().getMentor().getLastName())
                .submissionStatus(s.getStatus())
                .fileUrl(s.getFileUrl())
                .uploadedFilePath(s.getUploadedFilePath())
                .feedback(s.getFeedBack())
                .submittedAt(s.getSubmittedAt())
                .build();
    }
}
