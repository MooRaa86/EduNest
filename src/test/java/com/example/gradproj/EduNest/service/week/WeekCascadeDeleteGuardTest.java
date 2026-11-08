package com.example.gradproj.EduNest.service.week;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WeekCascadeDeleteGuardTest {

    private MentorShipRepository mentorShipRepository;
    private WeekRepository weekRepository;
    private TaskRepository taskRepository;
    private QuizRepository quizRepository;
    private ProjectRepository projectRepository;
    private LiveSessionRepository sessionRepository;
    private LectureRepository lectureRepository;
    private MentorRepository mentorRepository;
    private TaskSubmissionRepository taskSubmissionRepository;
    private ProjectSubmissionRepository projectSubmissionRepository;
    private QuizSubmissionRepository quizSubmissionRepository;
    private SessionAttendanceResultRepository sessionAttendanceResultRepository;
    private AttendanceRepository attendanceRepository;
    private WeekService weekService;

    private static final String MENTOR_EMAIL = "mentor@test.org";
    private static final Long MENTOR_ID = 10L;
    private static final Long WEEK_ID = 50L;

    @BeforeEach
    void setUp() {
        mentorShipRepository = mock(MentorShipRepository.class);
        weekRepository = mock(WeekRepository.class);
        taskRepository = mock(TaskRepository.class);
        quizRepository = mock(QuizRepository.class);
        projectRepository = mock(ProjectRepository.class);
        sessionRepository = mock(LiveSessionRepository.class);
        lectureRepository = mock(LectureRepository.class);
        mentorRepository = mock(MentorRepository.class);
        taskSubmissionRepository = mock(TaskSubmissionRepository.class);
        projectSubmissionRepository = mock(ProjectSubmissionRepository.class);
        quizSubmissionRepository = mock(QuizSubmissionRepository.class);
        sessionAttendanceResultRepository = mock(SessionAttendanceResultRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);

        weekService = new WeekService(
                mentorShipRepository,
                weekRepository,
                taskRepository,
                quizRepository,
                projectRepository,
                sessionRepository,
                lectureRepository,
                mentorRepository,
                taskSubmissionRepository,
                projectSubmissionRepository,
                quizSubmissionRepository,
                sessionAttendanceResultRepository,
                attendanceRepository
        );

        SecurityContextHolder.clearContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(MENTOR_EMAIL, "n/a", List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
        );
        SecurityContextHolder.setContext(context);

        Mentor mentor = Mentor.builder().id(MENTOR_ID).email(MENTOR_EMAIL).build();
        when(mentorRepository.findByEmail(anyString())).thenReturn(Optional.of(mentor));
        when(mentorRepository.findByEmail(eq(MENTOR_EMAIL))).thenReturn(Optional.of(mentor));

        MentorShip mentorship = MentorShip.builder().id(100L).mentor(mentor).build();
        Week week = Week.builder().id(WEEK_ID).mentorship(mentorship).build();
        when(weekRepository.findById(WEEK_ID)).thenReturn(Optional.of(week));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(week));
    }

    @Test
    @DisplayName("rejects deletion when live session is LIVE under the week")
    void rejectsDeletionWhenLiveSessionIsLive() {
        when(sessionRepository.existsByWeek_IdAndStatus(WEEK_ID, SessionStatus.LIVE)).thenReturn(true);

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete week that contains submissions or session attendance records");

        verify(weekRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("rejects deletion when session attendance snapshot exists under the week")
    void rejectsDeletionWhenSessionAttendanceExists() {
        when(attendanceRepository.existsBySession_Week_Id(WEEK_ID)).thenReturn(true);

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete week that contains submissions or session attendance records");

        verify(weekRepository, never()).deleteById(anyLong());
    }


    @Test
    @DisplayName("rejects deletion when task submission exists under the week")
    void rejectsDeletionWhenTaskSubmissionExists() {
        when(taskSubmissionRepository.existsByTask_Week_Id(WEEK_ID)).thenReturn(true);

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete week that contains submissions or session attendance records");

        verify(weekRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("rejects deletion when project submission exists under the week")
    void rejectsDeletionWhenProjectSubmissionExists() {
        when(projectSubmissionRepository.existsByProject_Week_Id(WEEK_ID)).thenReturn(true);

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete week that contains submissions or session attendance records");

        verify(weekRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("rejects deletion when scored quiz attempt exists under the week")
    void rejectsDeletionWhenScoredQuizSubmissionExists() {
        when(quizSubmissionRepository.existsByQuiz_Week_IdAndScoreIsNotNull(WEEK_ID)).thenReturn(true);

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete week that contains submissions or session attendance records");

        verify(weekRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("rejects deletion when session attendance result exists under the week")
    void rejectsDeletionWhenSessionAttendanceResultExists() {
        when(sessionAttendanceResultRepository.existsBySession_Week_Id(WEEK_ID)).thenReturn(true);

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete week that contains submissions or session attendance records");

        verify(weekRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("allows deletion when no submissions or attendance exist")
    void allowsDeletionWhenNoSubmissionsExist() {
        when(taskSubmissionRepository.existsByTask_Week_Id(WEEK_ID)).thenReturn(false);
        when(projectSubmissionRepository.existsByProject_Week_Id(WEEK_ID)).thenReturn(false);
        when(quizSubmissionRepository.existsByQuiz_Week_IdAndScoreIsNotNull(WEEK_ID)).thenReturn(false);
        when(sessionAttendanceResultRepository.existsBySession_Week_Id(WEEK_ID)).thenReturn(false);

        assertThatCode(() -> weekService.deleteWeek(WEEK_ID)).doesNotThrowAnyException();

        verify(weekRepository).findForUpdateById(WEEK_ID);
        verify(weekRepository).deleteById(WEEK_ID);
    }

    @Test
    @DisplayName("rejects deletion when caller is not the mentor owner")
    void rejectsDeletionWhenNotOwner() {
        Mentor otherMentor = Mentor.builder().id(999L).email("other@test.org").build();
        MentorShip otherMentorship = MentorShip.builder().id(200L).mentor(otherMentor).build();
        Week otherWeek = Week.builder().id(WEEK_ID).mentorship(otherMentorship).build();
        when(weekRepository.findById(WEEK_ID)).thenReturn(Optional.of(otherWeek));

        assertThatThrownBy(() -> weekService.deleteWeek(WEEK_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not authorized to access this week");

        verify(weekRepository, never()).deleteById(anyLong());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
}
