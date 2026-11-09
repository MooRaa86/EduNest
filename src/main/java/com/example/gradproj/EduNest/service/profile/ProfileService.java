package com.example.gradproj.EduNest.service.profile;

import com.example.gradproj.EduNest.dto.badges.response.BadgeAwardResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.profile.EnrolledMentorshipProgressDto;
import com.example.gradproj.EduNest.dto.profile.FullProfileStudentInformationForMentorResponse;
import com.example.gradproj.EduNest.dto.profile.ProfileStudentInformationForMentorResponse;
import com.example.gradproj.EduNest.dto.profile.StudentProjectProfileDTO;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.EnrolledMentorshipProgressResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.StudentMentorProfileKpiResponse;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final BadgeAwardRepository badgeAwardRepository;

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated user");
        }
        return authentication.getName();
    }

    private Long getCurrentMentorId() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"))
                .getId();
    }

    private void validateMentorHasAccessToStudent(Long studentId) {
        Long mentorId = getCurrentMentorId();
        boolean hasAccess = enrollmentRepository.existsByMentorIdAndStudentId(mentorId, studentId);
        if (!hasAccess) {
            throw new AccessDeniedException("You are not authorized to access this student's profile");
        }
    }
    @PreAuthorize("hasRole('MENTOR')")
    public ProfileStudentInformationForMentorResponse profileStudentInformationForMentorResponse(Long studentId){
        validateMentorHasAccessToStudent(studentId);
        Long mentorId = getCurrentMentorId();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        StudentMentorProfileKpiResponse kpi =
                enrollmentRepository.getStudentMentorProfileKpis(mentorId, student.getId());

        Long active = (kpi != null && kpi.getActiveMentorships() != null) ? kpi.getActiveMentorships() : 0L;
        Long completed = (kpi != null && kpi.getCompletedMentorships() != null) ? kpi.getCompletedMentorships() : 0L;
        Integer totalPoints = (kpi != null && kpi.getTotalPoints() != null) ? kpi.getTotalPoints() : 0;

        String facebookLink = student.getSocialMediaLinks().stream()
                .filter(s -> "Facebook".equalsIgnoreCase(String.valueOf(s.getName())))
                .map(SocialMedia::getUrl)
                .findFirst()
                .orElse(null);

        String linkedInLink = student.getSocialMediaLinks().stream()
                .filter(s -> "LinkedIn".equalsIgnoreCase(String.valueOf(s.getName())))
                .map(SocialMedia::getUrl)
                .findFirst()
                .orElse(null);

        String githubLink = student.getSocialMediaLinks().stream()
                .filter(s -> "GitHub".equalsIgnoreCase(String.valueOf(s.getName())))
                .map(SocialMedia::getUrl)
                .findFirst()
                .orElse(null);

        return ProfileStudentInformationForMentorResponse.builder()
                .imageUrl(student.getProfileImageUrl())
                .name((student.getFirstName() + " " + student.getLastName()).trim())
                .email(student.getEmail())
                .address(student.getAddress())
                .activeMentorships(active)
                .completedMentorships(completed)
                .totalPoints(totalPoints)
                .facebookLink(facebookLink)
                .linkedInLink(linkedInLink)
                .githubLink(githubLink)
                .build();
    }
    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<EnrolledMentorshipProgressDto> getEnrolledMentorshipProgress(Long studentId, Pageable pageable){
        validateMentorHasAccessToStudent(studentId);
        Long mentorId = getCurrentMentorId();
        Page<EnrolledMentorshipProgressResponse> page =
                enrollmentRepository.findEnrolledMentorshipsProgressForMentorAndStudent(
                        mentorId,
                    studentId,
                    pageable
            );

    List<EnrolledMentorshipProgressDto> content = page.getContent().stream()
            .map(p -> EnrolledMentorshipProgressDto.builder()
                    .imageUrl(p.getImageUrl())
                    .mentorshipId(p.getMentorshipId())
                    .title(p.getTitle())
                    .status(p.getStatus())
                    .totalPoints(p.getTotalPoints() == null ? 0 :  p.getTotalPoints())
                    .totalTasks(p.getTotalTasks() == null ? 0L : p.getTotalTasks())
                    .submittedTasks(p.getSubmittedTasks() == null ? 0L : p.getSubmittedTasks())
                    .totalQuizzes(p.getTotalQuizzes() == null ? 0L : p.getTotalQuizzes())
                    .submittedQuizzes(p.getSubmittedQuizzes() == null ? 0L : p.getSubmittedQuizzes())
                    .build())
            .toList();

    return PageResponse.<EnrolledMentorshipProgressDto>builder()
            .content(content)
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .build();
}

    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<StudentProjectProfileDTO> getStudentProjects(Long studentId, int page, int size) {
        validateMentorHasAccessToStudent(studentId);

        Pageable pageable = PageRequest.of(page, size);

        Page<ProjectSubmission> submissions =
                projectSubmissionRepository
                        .findForStudentProfile(studentId, null, pageable);

        List<StudentProjectProfileDTO> content =
                submissions.getContent()
                        .stream()
                        .map(this::mapToDto)
                        .toList();

        return PageResponse.<StudentProjectProfileDTO>builder()
                .content(content)
                .page(submissions.getNumber())
                .size(submissions.getSize())
                .totalElements(submissions.getTotalElements())
                .totalPages(submissions.getTotalPages())
                .build();
    }

    private StudentProjectProfileDTO mapToDto(ProjectSubmission ps) {
        return StudentProjectProfileDTO.builder()
                .projectId(ps.getProject().getPublicId())
                .projectSubmissionId(ps.getPublicId())
                .projectTitle(ps.getProject().getTitle())
                .mentorshipId(
                        ps.getProject()
                                .getWeek()
                                .getMentorship()
                                .getPublicId()
                )
                .mentorshipTitle(
                        ps.getProject()
                                .getWeek()
                                .getMentorship()
                                .getTitle()
                )
                .status(ps.getStatus())
                .submittedAt(ps.getSubmittedAt())
                .gradedAt(ps.getGradedAt())
                .submissionLink(ps.getFileUrl())
                .feedback(ps.getFeedBack())
                .rawScore(ps.getRawScore())
                .finalScore(ps.getFinalScore())
                .build();
    }

    private Long resolveStudentId(UUID studentPublicId) {
        return studentRepository.findIdByPublicId(studentPublicId)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found with UUID: " + studentPublicId));
    }

    @PreAuthorize("hasRole('MENTOR')")
    public ProfileStudentInformationForMentorResponse profileStudentInformationForMentorResponse(UUID studentPublicId) {
        return profileStudentInformationForMentorResponse(resolveStudentId(studentPublicId));
    }

    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<EnrolledMentorshipProgressDto> getEnrolledMentorshipProgress(UUID studentPublicId, Pageable pageable) {
        return getEnrolledMentorshipProgress(resolveStudentId(studentPublicId), pageable);
    }

    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<StudentProjectProfileDTO> getStudentProjects(UUID studentPublicId, int page, int size) {
        return getStudentProjects(resolveStudentId(studentPublicId), page, size);
    }

    @PreAuthorize("hasRole('MENTOR')")
    public FullProfileStudentInformationForMentorResponse getFullStudentProfileForMentor(
            UUID studentPublicId,
            int mentorshipsPage,
            int mentorshipsSize,
            int projectsPage,
            int projectsSize
    ) {
        return getFullStudentProfileForMentor(resolveStudentId(studentPublicId), mentorshipsPage, mentorshipsSize, projectsPage, projectsSize);
    }
    @PreAuthorize("hasRole('MENTOR')")
    public FullProfileStudentInformationForMentorResponse getFullStudentProfileForMentor(
            Long studentId,
            int mentorshipsPage,
            int mentorshipsSize,
            int projectsPage,
            int projectsSize
    ) {

        ProfileStudentInformationForMentorResponse profile =
                profileStudentInformationForMentorResponse(studentId);

        PageResponse<EnrolledMentorshipProgressDto> mentorshipsProgress =
                getEnrolledMentorshipProgress(
                        studentId,
                        PageRequest.of(mentorshipsPage, mentorshipsSize)
                );

        PageResponse<StudentProjectProfileDTO> projects =
                getStudentProjects(
                        studentId,
                        projectsPage,
                        projectsSize
                );

        List<BadgeAwardResponse> badges = badgeAwardRepository
                .findByStudent_IdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(a -> BadgeAwardResponse.builder()
                        .id(a.getPublicId())
                        .badgeId(a.getBadge().getPublicId())
                        .badgeTitle(a.getBadge().getTitle())
                        .studentId(a.getStudent().getPublicId())
                        .studentFullName(a.getStudent().getFirstName() + " " + a.getStudent().getLastName())
                        .awardedById(a.getAwardedBy().getPublicId())
                        .awardedAt(a.getCreatedAt())
                        .note(a.getNote())
                        .badgePoints(a.getBadge().getPoints())
                        .build())
                .toList();

        return FullProfileStudentInformationForMentorResponse.builder()
                .profileStudentInformationForMentorResponse(profile)
                .enrolledMentorshipProgressDtoPageResponse(mentorshipsProgress)
                .projectProfileDTOPageResponse(projects)
                .badges(badges)
                .build();
    }
}
