package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.config.learning.LateSubmissionProperties;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * EB-53 — exact grace boundary for task submissions.
 *
 * <p>The rejection decision compares exact timestamps
 * (now.isAfter(dueAt.plusMinutes(grace))), so the boundary itself is
 * inclusive and even one second beyond it is rejected — partial minutes
 * no longer sneak past the grace. Time is pinned via a static mock of
 * {@link LocalDateTime#now()} so the boundary cases are deterministic.
 */
class TaskSubmissionGraceBoundaryTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0, 0);
    private static final int GRACE_MINUTES = 10; // LateSubmissionProperties default

    private static final String STUDENT_EMAIL = "boundary-student@edunest.org";
    private static final Long TASK_ID = 1L;
    private static final Long STUDENT_ID = 20L;

    private TaskRepository taskRepository;
    private TaskSubmissionRepository submissionRepository;
    private StudentRepository studentRepository;
    private TotalPointsServiceImp totalPointsService;
    private EnrollmentRepository enrollmentRepository;
    private NotificationService notificationService;
    private TaskSubmissionServiceImpl service;

    private Task task;
    private Student student;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        submissionRepository = mock(TaskSubmissionRepository.class);
        studentRepository = mock(StudentRepository.class);
        totalPointsService = mock(TotalPointsServiceImp.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        notificationService = mock(NotificationService.class);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(STUDENT_EMAIL);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Mentor mentor = Mentor.builder().email("mentor@edunest.org").build();
        mentor.setId(100L);
        MentorShip mentorship = MentorShip.builder().id(200L).mentor(mentor).build();
        Week week = Week.builder().id(300L).mentorship(mentorship).build();

        student = Student.builder().id(STUDENT_ID).firstName("Ali").lastName("Hassan")
                .email(STUDENT_EMAIL).build();

        task = Task.builder()
                .id(TASK_ID)
                .week(week)
                .title("Boundary Task")
                .description("EB-53 grace boundary")
                .points(10).passPoints(5).estimatedMinutes(30)
                .status(TaskStatus.PUBLISHED)
                .dueAt(FIXED_NOW)
                .build();

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.getReferenceById(TASK_ID)).thenReturn(task);
        when(studentRepository.findIdByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(STUDENT_ID));
        when(studentRepository.getReferenceById(STUDENT_ID)).thenReturn(student);
        when(enrollmentRepository.isStudentEnrolledForTask(TASK_ID, STUDENT_ID)).thenReturn(true);
        when(submissionRepository.save(any(TaskSubmission.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new TaskSubmissionServiceImpl(
                taskRepository, submissionRepository, studentRepository,
                totalPointsService, enrollmentRepository, mock(MentorRepository.class),
                mock(FileStorageService.class), notificationService, mock(WeekRepository.class),
                mock(EntityManager.class), new LateSubmissionProperties());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TaskSubmissionResponse submitInsidePinnedClock() {
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            return service.submit(TASK_ID, null, "https://storage.example.org/eb53/boundary.pdf");
        }
    }

    private void expectGraceRejection() {
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            assertThatThrownBy(() -> service.submit(TASK_ID, null, "https://storage.example.org/eb53/boundary.pdf"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("grace period has passed");
        }
        // rejection happens BEFORE any state or points mutation
        verify(submissionRepository, never()).save(any(TaskSubmission.class));
        verify(totalPointsService, never()).applyDelta(any(), any(), anyInt());
        verify(notificationService, never()).sendToUserByEmail(anyString(), anyString(), anyString(), any(NotificationType.class));
        verify(studentRepository, never()).getReferenceById(any());
        verify(taskRepository, never()).getReferenceById(any());
    }

    @Test
    @DisplayName("Submission exactly at the deadline is accepted, not flagged late")
    void exactlyAtDeadlineAccepted() {
        task.setDueAt(FIXED_NOW);

        TaskSubmissionResponse response = submitInsidePinnedClock();

        assertThat(response.getIsLate()).isFalse();
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.getSubmittedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("Submission exactly at deadline + grace (the boundary itself) is accepted and flagged late")
    void exactlyAtDeadlinePlusGraceAccepted() {
        task.setDueAt(FIXED_NOW.minusMinutes(GRACE_MINUTES));

        TaskSubmissionResponse response = submitInsidePinnedClock();

        assertThat(response.getIsLate()).isTrue();
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.getSubmittedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("Submission one second beyond deadline + grace is rejected with no state or points mutation")
    void oneSecondBeyondGraceRejected() {
        task.setDueAt(FIXED_NOW.minusMinutes(GRACE_MINUTES).minusSeconds(1));

        expectGraceRejection();
    }

    @Test
    @DisplayName("A partial minute beyond the grace is rejected (regression: truncated minutesLate no longer accepts it)")
    void partialMinuteBeyondGraceRejected() {
        // 10 min + 30 s late: old ChronoUnit.MINUTES truncation computed minutesLate == 10 and accepted this.
        task.setDueAt(FIXED_NOW.minusSeconds(630));

        expectGraceRejection();
    }

    @Test
    @DisplayName("Late resubmission stays rejected even within the grace")
    void lateResubmissionStillRejected() {
        task.setDueAt(FIXED_NOW.minusSeconds(60));
        when(submissionRepository.findByTask_IdAndStudent_Id(TASK_ID, STUDENT_ID))
                .thenReturn(Optional.of(new TaskSubmission()));

        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            assertThatThrownBy(() -> service.submit(TASK_ID, null, "https://storage.example.org/eb53/resubmit.pdf"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("can't resubmit");
        }
        verify(submissionRepository, never()).save(any(TaskSubmission.class));
    }
}
