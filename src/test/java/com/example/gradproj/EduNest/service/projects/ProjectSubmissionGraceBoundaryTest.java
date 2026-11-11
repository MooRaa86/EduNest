package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.config.learning.LateSubmissionProperties;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.ContentReadGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EB-53 — exact grace boundary for project submissions.
 *
 * <p>The rejection decision compares exact timestamps
 * (now.isAfter(endAt.plusMinutes(grace))), so the boundary itself is
 * inclusive and even one second beyond it is rejected — partial minutes
 * no longer sneak past the grace. Time is pinned via a static mock of
 * {@link LocalDateTime#now()} so the boundary cases are deterministic.
 */
class ProjectSubmissionGraceBoundaryTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0, 0);
    private static final int GRACE_MINUTES = 10; // LateSubmissionProperties default

    private static final String STUDENT_EMAIL = "boundary-student@edunest.org";
    private static final Long PROJECT_ID = 1L;
    private static final Long STUDENT_ID = 20L;

    private ProjectRepository projectRepository;
    private ProjectSubmissionRepository projectSubmissionRepository;
    private StudentRepository studentRepository;
    private TotalPointsServiceImp totalPointsService;
    private EnrollmentRepository enrollmentRepository;
    private NotificationService notificationService;
    private ProjectSubmissionServiceImpl service;

    private Project project;
    private Student student;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectSubmissionRepository = mock(ProjectSubmissionRepository.class);
        studentRepository = mock(StudentRepository.class);
        totalPointsService = mock(TotalPointsServiceImp.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        notificationService = mock(NotificationService.class);

        Mentor mentor = Mentor.builder().email("mentor@edunest.org").build();
        mentor.setId(100L);
        MentorShip mentorship = MentorShip.builder().id(200L).mentor(mentor).build();
        Week week = Week.builder().id(300L).mentorship(mentorship).build();

        student = Student.builder().id(STUDENT_ID).firstName("Ali").lastName("Hassan")
                .email(STUDENT_EMAIL).build();

        project = Project.builder()
                .id(PROJECT_ID)
                .week(week)
                .title("Boundary Project")
                .brief("EB-53 grace boundary")
                .goal("EB-53 grace boundary")
                .startAt(FIXED_NOW.minusMinutes(5))
                .points(10)
                .status(ProjectStatus.PUBLISHED)
                .endAt(FIXED_NOW)
                .build();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.getReferenceById(PROJECT_ID)).thenReturn(project);
        when(studentRepository.findIdByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(STUDENT_ID));
        when(studentRepository.getReferenceById(STUDENT_ID)).thenReturn(student);
        when(enrollmentRepository.isStudentEnrolledForProjectByEmail(PROJECT_ID, STUDENT_EMAIL)).thenReturn(true);
        when(projectSubmissionRepository.save(any(ProjectSubmission.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ProjectSubmissionServiceImpl(
                projectRepository, projectSubmissionRepository, studentRepository,
                totalPointsService, enrollmentRepository, mock(MentorRepository.class),
                mock(FileStorageService.class), notificationService, mock(ContentReadGuard.class),
                mock(WeekRepository.class), mock(EntityManager.class), new LateSubmissionProperties());
    }

    private ProjectSubmissionResponse submitInsidePinnedClock() {
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            return service.submit(PROJECT_ID, null, "https://storage.example.org/eb53/boundary.zip", STUDENT_EMAIL);
        }
    }

    private void expectGraceRejection() {
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            assertThatThrownBy(() -> service.submit(PROJECT_ID, null, "https://storage.example.org/eb53/boundary.zip", STUDENT_EMAIL))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("grace period has passed");
        }
        // rejection happens BEFORE any state or points mutation
        verify(projectSubmissionRepository, never()).save(any(ProjectSubmission.class));
        verify(totalPointsService, never()).applyDelta(any(), any(), anyInt());
        verify(notificationService, never()).sendToUserByEmail(anyString(), anyString(), anyString(), any(NotificationType.class));
        verify(studentRepository, never()).getReferenceById(any());
        verify(projectRepository, never()).getReferenceById(any());
    }

    @Test
    @DisplayName("Submission exactly at the deadline is accepted, not flagged late")
    void exactlyAtDeadlineAccepted() {
        project.setEndAt(FIXED_NOW);

        ProjectSubmissionResponse response = submitInsidePinnedClock();

        assertThat(response.getIsLate()).isFalse();
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.getSubmittedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("Submission exactly at deadline + grace (the boundary itself) is accepted and flagged late")
    void exactlyAtDeadlinePlusGraceAccepted() {
        project.setEndAt(FIXED_NOW.minusMinutes(GRACE_MINUTES));

        ProjectSubmissionResponse response = submitInsidePinnedClock();

        assertThat(response.getIsLate()).isTrue();
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.getSubmittedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("Submission one second beyond deadline + grace is rejected with no state or points mutation")
    void oneSecondBeyondGraceRejected() {
        project.setEndAt(FIXED_NOW.minusMinutes(GRACE_MINUTES).minusSeconds(1));

        expectGraceRejection();
    }

    @Test
    @DisplayName("A partial minute beyond the grace is rejected (regression: truncated minutesLate no longer accepts it)")
    void partialMinuteBeyondGraceRejected() {
        // 10 min + 30 s late: old ChronoUnit.MINUTES truncation computed minutesLate == 10 and accepted this.
        project.setEndAt(FIXED_NOW.minusSeconds(630));

        expectGraceRejection();
    }

    @Test
    @DisplayName("Late resubmission stays rejected even within the grace")
    void lateResubmissionStillRejected() {
        project.setEndAt(FIXED_NOW.minusSeconds(60));
        when(projectSubmissionRepository.findByProject_IdAndStudent_Id(PROJECT_ID, STUDENT_ID))
                .thenReturn(Optional.of(new ProjectSubmission()));

        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            assertThatThrownBy(() -> service.submit(PROJECT_ID, null, "https://storage.example.org/eb53/resubmit.zip", STUDENT_EMAIL))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("can't resubmit");
        }
        verify(projectSubmissionRepository, never()).save(any(ProjectSubmission.class));
    }
}
