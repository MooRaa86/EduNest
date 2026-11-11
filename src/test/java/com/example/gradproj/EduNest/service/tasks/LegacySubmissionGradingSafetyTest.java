package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.LegacySubmissionAuditRequiredException;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.projects.ProjectSubmissionServiceImpl;
import com.example.gradproj.EduNest.service.security.ContentReadGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EB-51 D-09a: Integrity verification for Task and Project grading paths.
 *
 * <p>Verifies:
 * 1. Fresh submissions with null pointsApplied grade cleanly for the first time.
 * 2. Regrading submissions with known pointsApplied computes correct positive and negative deltas.
 * 3. Historical legacy graded submissions without pointsApplied provenance fail-closed with
 *    {@link LegacySubmissionAuditRequiredException} to prevent duplicate credit.
 */
class LegacySubmissionGradingSafetyTest {

    private TaskRepository taskRepository;
    private TaskSubmissionRepository taskSubmissionRepository;
    private ProjectRepository projectRepository;
    private ProjectSubmissionRepository projectSubmissionRepository;
    private WeekRepository weekRepository;
    private TotalPointsServiceImp totalPointsService;
    private NotificationService notificationService;
    private MentorRepository mentorRepository;
    private StudentRepository studentRepository;
    private EnrollmentRepository enrollmentRepository;

    private TaskSubmissionServiceImpl taskSubmissionService;
    private ProjectSubmissionServiceImpl projectSubmissionService;

    private final String MENTOR_EMAIL = "mentor@edunest.org";
    private Mentor mentor;
    private MentorShip mentorship;
    private Week week;
    private Student student;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskSubmissionRepository = mock(TaskSubmissionRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectSubmissionRepository = mock(ProjectSubmissionRepository.class);
        weekRepository = mock(WeekRepository.class);
        totalPointsService = mock(TotalPointsServiceImp.class);
        notificationService = mock(NotificationService.class);
        mentorRepository = mock(MentorRepository.class);
        studentRepository = mock(StudentRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(MENTOR_EMAIL);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        mentor = Mentor.builder().email(MENTOR_EMAIL).build();
        mentor.setId(100L);
        when(mentorRepository.findByEmail(MENTOR_EMAIL)).thenReturn(Optional.of(mentor));

        mentorship = MentorShip.builder().id(200L).mentor(mentor).build();
        week = Week.builder().id(300L).mentorship(mentorship).build();
        when(weekRepository.findForUpdateById(300L)).thenReturn(Optional.of(week));

        student = Student.builder().email("student@edunest.org").firstName("Ali").lastName("Hassan").build();
        student.setId(400L);

        taskSubmissionService = new TaskSubmissionServiceImpl(
                taskRepository,
                taskSubmissionRepository,
                studentRepository,
                totalPointsService,
                enrollmentRepository,
                mentorRepository,
                mock(FileStorageService.class),
                notificationService,
                weekRepository,
                mock(EntityManager.class),
                new com.example.gradproj.EduNest.config.learning.LateSubmissionProperties()
        );

        ContentReadGuard contentReadGuard = mock(ContentReadGuard.class);
        when(contentReadGuard.currentEmail()).thenReturn(MENTOR_EMAIL);

        projectSubmissionService = new ProjectSubmissionServiceImpl(
                projectRepository,
                projectSubmissionRepository,
                studentRepository,
                totalPointsService,
                enrollmentRepository,
                mentorRepository,
                mock(FileStorageService.class),
                notificationService,
                contentReadGuard,
                weekRepository,
                mock(EntityManager.class),
                new com.example.gradproj.EduNest.config.learning.LateSubmissionProperties()
        );
    }

    @Test
    @DisplayName("Fresh task submission (pointsApplied == 0, status == SUBMITTED) grades for the first time")
    void freshTaskSubmission_gradesSuccessfully() {
        Task task = Task.builder().week(week).points(50).status(TaskStatus.PUBLISHED).title("Java Basics").build();
        task.setId(10L);
        when(taskRepository.findForUpdateById(10L)).thenReturn(Optional.of(task));

        TaskSubmission sub = TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(0)
                .rawScore(null)
                .finalScore(null)
                .gradedAt(null)
                .build();
        sub.setId(1L);
        when(taskSubmissionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.findForUpdateById(1L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
        req.setScore(40);
        req.setFeedback("Good work");

        TaskSubmissionResponse resp = taskSubmissionService.grade(1L, req);

        assertThat(resp.getFinalScore()).isEqualTo(40);
        assertThat(sub.getPointsApplied()).isEqualTo(40);
        assertThat(sub.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        verify(totalPointsService).applyDelta(student, mentorship, 40);
    }

    @Test
    @DisplayName("Regrading task submission with existing pointsApplied computes correct delta")
    void regradingTaskSubmission_computesDeltaCorrectly() {
        Task task = Task.builder().week(week).points(50).status(TaskStatus.PUBLISHED).title("Java Basics").build();
        task.setId(10L);
        when(taskRepository.findForUpdateById(10L)).thenReturn(Optional.of(task));

        TaskSubmission sub = TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.GRADED)
                .pointsApplied(30)
                .finalScore(30)
                .gradedAt(LocalDateTime.now().minusDays(1))
                .build();
        sub.setId(1L);
        when(taskSubmissionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.findForUpdateById(1L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
        req.setScore(45);
        req.setFeedback("Revised score");

        taskSubmissionService.grade(1L, req);

        assertThat(sub.getPointsApplied()).isEqualTo(45);
        // delta = 45 - 30 = +15
        verify(totalPointsService).applyDelta(student, mentorship, 15);
    }

    @Test
    @DisplayName("Regrading task submission with lower score computes negative delta")
    void regradingTaskSubmission_lowerScore_computesNegativeDelta() {
        Task task = Task.builder().week(week).points(50).status(TaskStatus.PUBLISHED).title("Java Basics").build();
        task.setId(10L);
        when(taskRepository.findForUpdateById(10L)).thenReturn(Optional.of(task));

        TaskSubmission sub = TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.GRADED)
                .pointsApplied(40)
                .finalScore(40)
                .gradedAt(LocalDateTime.now().minusDays(1))
                .build();
        sub.setId(1L);
        when(taskSubmissionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.findForUpdateById(1L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
        req.setScore(25);
        req.setFeedback("Penalty for late revision");

        taskSubmissionService.grade(1L, req);

        assertThat(sub.getPointsApplied()).isEqualTo(25);
        // delta = 25 - 40 = -15
        verify(totalPointsService).applyDelta(student, mentorship, -15);
    }

    @Test
    @DisplayName("Legacy graded task submission without pointsApplied throws LegacySubmissionAuditRequiredException")
    void legacyGradedTaskSubmission_withoutPointsApplied_failsClosed() {
        Task task = Task.builder().week(week).points(50).title("Legacy Task").build();
        task.setId(10L);
        when(taskRepository.findForUpdateById(10L)).thenReturn(Optional.of(task));

        TaskSubmission legacySub = TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.GRADED)
                .finalScore(35)
                .pointsApplied(null) // legacy missing provenance
                .gradedAt(LocalDateTime.now().minusMonths(2))
                .build();
        legacySub.setId(99L);
        when(taskSubmissionRepository.findById(99L)).thenReturn(Optional.of(legacySub));
        when(taskSubmissionRepository.findForUpdateById(99L)).thenReturn(Optional.of(legacySub));

        GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
        req.setScore(40);
        req.setFeedback("Attempted regrade");

        assertThatThrownBy(() -> taskSubmissionService.grade(99L, req))
                .isInstanceOf(LegacySubmissionAuditRequiredException.class)
                .hasMessageContaining("lacks recorded points provenance");

        verifyNoInteractions(totalPointsService);
    }

    @Test
    @DisplayName("Legacy graded project submission without pointsApplied throws LegacySubmissionAuditRequiredException")
    void legacyGradedProjectSubmission_withoutPointsApplied_failsClosed() {
        Project project = Project.builder().week(week).points(100).title("Legacy Project").build();
        project.setId(20L);
        when(projectRepository.findForUpdateById(20L)).thenReturn(Optional.of(project));

        ProjectSubmission legacySub = ProjectSubmission.builder()
                .project(project)
                .student(student)
                .status(SubmissionStatus.GRADED)
                .finalScore(80)
                .pointsApplied(null) // legacy missing provenance
                .gradedAt(LocalDateTime.now().minusMonths(2))
                .build();
        legacySub.setId(88L);
        when(projectSubmissionRepository.findById(88L)).thenReturn(Optional.of(legacySub));
        when(projectSubmissionRepository.findForUpdateById(88L)).thenReturn(Optional.of(legacySub));

        GradeProjectSubmissionRequest req = new GradeProjectSubmissionRequest();
        req.setScore(90);
        req.setFeedback("Attempted project regrade");

        assertThatThrownBy(() -> projectSubmissionService.gradeProject(88L, req, MENTOR_EMAIL))
                .isInstanceOf(LegacySubmissionAuditRequiredException.class)
                .hasMessageContaining("lacks recorded points provenance");

        verifyNoInteractions(totalPointsService);
    }

    @Test
    @DisplayName("Fresh project submission (pointsApplied == 0, status == SUBMITTED) grades successfully")
    void freshProjectSubmission_gradesSuccessfully() {
        Project project = Project.builder().week(week).points(100).title("Capstone").build();
        project.setId(20L);
        when(projectRepository.findForUpdateById(20L)).thenReturn(Optional.of(project));

        ProjectSubmission sub = ProjectSubmission.builder()
                .project(project)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(0)
                .rawScore(null)
                .finalScore(null)
                .gradedAt(null)
                .build();
        sub.setId(89L);
        when(projectSubmissionRepository.findById(89L)).thenReturn(Optional.of(sub));
        when(projectSubmissionRepository.findForUpdateById(89L)).thenReturn(Optional.of(sub));
        when(projectSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GradeProjectSubmissionRequest req = new GradeProjectSubmissionRequest();
        req.setScore(85);
        req.setFeedback("Solid architecture");

        projectSubmissionService.gradeProject(89L, req, MENTOR_EMAIL);

        assertThat(sub.getPointsApplied()).isEqualTo(85);
        assertThat(sub.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        verify(totalPointsService).applyDelta(student, mentorship, 85);
    }

    @Test
    @DisplayName("Historical genuinely ungraded task submission with pointsApplied == null is rejected pending audit")
    void historicalUngradedTaskSubmission_withNullPointsApplied_isRejectedPendingAudit() {
        Task task = Task.builder().week(week).points(50).status(TaskStatus.PUBLISHED).title("Legacy Task").build();
        task.setId(10L);
        when(taskRepository.findForUpdateById(10L)).thenReturn(Optional.of(task));

        TaskSubmission sub = TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(null)
                .rawScore(null)
                .finalScore(null)
                .gradedAt(null)
                .build();
        sub.setId(15L);
        when(taskSubmissionRepository.findById(15L)).thenReturn(Optional.of(sub));
        when(taskSubmissionRepository.findForUpdateById(15L)).thenReturn(Optional.of(sub));

        GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
        req.setScore(40);
        req.setFeedback("Attempted grade on un-audited historical record");

        assertThatThrownBy(() -> taskSubmissionService.grade(15L, req))
                .isInstanceOf(LegacySubmissionAuditRequiredException.class)
                .hasMessageContaining("lacks recorded points provenance");

        verifyNoInteractions(totalPointsService);
    }

    @Test
    @DisplayName("Historical genuinely ungraded project submission with pointsApplied == null is rejected pending audit")
    void historicalUngradedProjectSubmission_withNullPointsApplied_isRejectedPendingAudit() {
        Project project = Project.builder().week(week).points(100).title("Legacy Project").build();
        project.setId(20L);
        when(projectRepository.findForUpdateById(20L)).thenReturn(Optional.of(project));

        ProjectSubmission sub = ProjectSubmission.builder()
                .project(project)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(null)
                .rawScore(null)
                .finalScore(null)
                .gradedAt(null)
                .build();
        sub.setId(25L);
        when(projectSubmissionRepository.findById(25L)).thenReturn(Optional.of(sub));
        when(projectSubmissionRepository.findForUpdateById(25L)).thenReturn(Optional.of(sub));

        GradeProjectSubmissionRequest req = new GradeProjectSubmissionRequest();
        req.setScore(85);
        req.setFeedback("Attempted grade on un-audited historical record");

        assertThatThrownBy(() -> projectSubmissionService.gradeProject(25L, req, MENTOR_EMAIL))
                .isInstanceOf(LegacySubmissionAuditRequiredException.class)
                .hasMessageContaining("lacks recorded points provenance");

        verifyNoInteractions(totalPointsService);
    }
}