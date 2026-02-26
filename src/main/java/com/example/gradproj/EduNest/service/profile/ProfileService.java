package com.example.gradproj.EduNest.service.profile;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.profile.EnrolledMentorshipProgressDto;
import com.example.gradproj.EduNest.dto.profile.FullProfileStudentInformationForMentorResponse;
import com.example.gradproj.EduNest.dto.profile.ProfileStudentInformationForMentorResponse;
import com.example.gradproj.EduNest.dto.profile.StudentProjectProfileDTO;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;

    private String getCurrentUserEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }
    public ProfileStudentInformationForMentorResponse profileStudentInformationForMentorResponse(Long studentId){

        Mentor mentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        StudentMentorProfileKpiResponse kpi =
                enrollmentRepository.getStudentMentorProfileKpis(mentor.getId(), student.getId());

        SocialMedia sm = student.getSocialMedia();

        Long active = (kpi != null && kpi.getActiveMentorships() != null) ? kpi.getActiveMentorships() : 0L;
        Long completed = (kpi != null && kpi.getCompletedMentorships() != null) ? kpi.getCompletedMentorships() : 0L;
        Integer totalPoints = (kpi != null && kpi.getTotalPoints() != null) ? kpi.getTotalPoints() : 0;

        return ProfileStudentInformationForMentorResponse.builder()
                .name((student.getFirstName() + " " + student.getLastName()).trim())
                .email(student.getEmail())
                .address(student.getAddress())
                .activeMentorships(active)
                .completedMentorships(completed)
                .totalPoints(totalPoints)
                .facebookLink(sm != null ? sm.getFacebook() : null)
                .linkedInLink(sm != null ? sm.getLinkedin() : null)
                .githubLink(sm != null ? sm.getGithub() : null)
                .build();
    }
public PageResponse<EnrolledMentorshipProgressDto> getEnrolledMentorshipProgress(Long studentId, Pageable pageable){
    Mentor mentor = mentorRepository.findByEmail(getCurrentUserEmail())
            .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));
    Page<EnrolledMentorshipProgressResponse> page =
            enrollmentRepository.findEnrolledMentorshipsProgressForMentorAndStudent(
                    mentor.getId(),
                    studentId,
                    pageable
            );

    List<EnrolledMentorshipProgressDto> content = page.getContent().stream()
            .map(p -> EnrolledMentorshipProgressDto.builder()
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

    public PageResponse<StudentProjectProfileDTO> getStudentProjects(
            Long studentId,
            SubmissionStatus status,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(page, size);

        Page<ProjectSubmission> submissions =
                projectSubmissionRepository
                        .findForStudentProfile(studentId, status, pageable);

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
                .projectSubmissionId(ps.getId())
                .projectTitle(ps.getProject().getTitle())
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
    public FullProfileStudentInformationForMentorResponse getFullStudentProfileForMentor(
            Long studentId,
            int mentorshipsPage,
            int mentorshipsSize,
            int projectsPage,
            int projectsSize,
            SubmissionStatus projectsStatus
    ) {
        if (projectsStatus == null) {
            projectsStatus = SubmissionStatus.SUBMITTED;
        }
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
                        projectsStatus,
                        projectsPage,
                        projectsSize
                );

        return FullProfileStudentInformationForMentorResponse.builder()
                .profileStudentInformationForMentorResponse(profile)
                .enrolledMentorshipProgressDtoPageResponse(mentorshipsProgress)
                .projectProfileDTOPageResponse(projects)
                .build();
    }
}
