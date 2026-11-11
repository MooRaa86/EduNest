package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PointsReconciliationServiceTest {

    private TotalPointsRepository totalPointsRepository;
    private TaskSubmissionRepository taskSubmissionRepository;
    private ProjectSubmissionRepository projectSubmissionRepository;
    private QuizSubmissionRepository quizSubmissionRepository;
    private BadgeAwardRepository badgeAwardRepository;
    private SessionAttendanceResultRepository sessionAttendanceResultRepository;
    private LiveSessionProperties liveSessionProperties;
    private PointsReconciliationService reconciliationService;

    private static final Long STUDENT_ID = 100L;
    private static final Long MENTORSHIP_ID = 10L;

    @BeforeEach
    void setUp() {
        totalPointsRepository = mock(TotalPointsRepository.class);
        taskSubmissionRepository = mock(TaskSubmissionRepository.class);
        projectSubmissionRepository = mock(ProjectSubmissionRepository.class);
        quizSubmissionRepository = mock(QuizSubmissionRepository.class);
        badgeAwardRepository = mock(BadgeAwardRepository.class);
        sessionAttendanceResultRepository = mock(SessionAttendanceResultRepository.class);
        liveSessionProperties = new LiveSessionProperties();

        reconciliationService = new PointsReconciliationService(
                totalPointsRepository,
                taskSubmissionRepository,
                projectSubmissionRepository,
                quizSubmissionRepository,
                badgeAwardRepository,
                sessionAttendanceResultRepository,
                liveSessionProperties
        );
    }

    @Test
    @DisplayName("Classifies as EXACT when all items have verified provenance and match ledger total exactly")
    void reconcilesExactWhenAllEvidenceMatchesLedger() {
        TotalPoints tp = TotalPoints.builder().totalPoints(150).build();
        when(totalPointsRepository.findByStudent_IdAndMentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Optional.of(tp));

        TaskSubmission taskSub = TaskSubmission.builder().pointsApplied(50).finalScore(50).build();
        when(taskSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(taskSub));

        ProjectSubmission projSub = ProjectSubmission.builder().pointsApplied(40).finalScore(40).build();
        when(projectSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(projSub));

        QuizSubmission quizSub = QuizSubmission.builder().score(30.0).build();
        when(quizSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(quizSub));

        Badge badge = Badge.builder().points(25).build();
        BadgeAward award = BadgeAward.builder().badge(badge).build();
        when(badgeAwardRepository.findByStudent_IdAndBadge_Mentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(award));

        SessionAttendanceResult attResult = SessionAttendanceResult.builder().attended(true).build();
        when(sessionAttendanceResultRepository.findAttendedByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(attResult)); // default 5 pts

        // 50 + 40 + 30 + 25 + 5 = 150
        PointsReconciliationReport report = reconciliationService.reconcileStudent(STUDENT_ID, MENTORSHIP_ID);

        assertThat(report.classification()).isEqualTo(ReconciliationClassification.EXACT);
        assertThat(report.ledgerTotalPoints()).isEqualTo(150);
        assertThat(report.totalVerifiablePoints()).isEqualTo(150);
        assertThat(report.unverifiedSubmissionsCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Classifies as PARTIAL when all items have recorded points but ledger differs by discrepancy")
    void reconcilesPartialWhenDiscrepancyExistsWithoutUnverifiedSubmissions() {
        TotalPoints tp = TotalPoints.builder().totalPoints(200).build(); // 50 points higher than itemized
        when(totalPointsRepository.findByStudent_IdAndMentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Optional.of(tp));

        TaskSubmission taskSub = TaskSubmission.builder().pointsApplied(100).build();
        when(taskSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(taskSub));
        when(projectSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(quizSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(badgeAwardRepository.findByStudent_IdAndBadge_Mentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(sessionAttendanceResultRepository.findAttendedByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());

        PointsReconciliationReport report = reconciliationService.reconcileStudent(STUDENT_ID, MENTORSHIP_ID);

        assertThat(report.classification()).isEqualTo(ReconciliationClassification.PARTIAL);
        assertThat(report.ledgerTotalPoints()).isEqualTo(200);
        assertThat(report.totalVerifiablePoints()).isEqualTo(100);
        assertThat(report.rationale()).contains("unexplained discrepancy of 100 points");
    }

    @Test
    @DisplayName("Classifies as PARTIAL when legacy graded submission exists without points_applied")
    void reconcilesPartialWhenLegacyGradedWithoutPointsApplied() {
        TotalPoints tp = TotalPoints.builder().totalPoints(80).build();
        when(totalPointsRepository.findByStudent_IdAndMentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Optional.of(tp));

        // Legacy graded task: finalScore is 80, but pointsApplied is null!
        TaskSubmission legacyTask = TaskSubmission.builder().finalScore(80).pointsApplied(null).build();
        when(taskSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(legacyTask));
        when(projectSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(quizSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(badgeAwardRepository.findByStudent_IdAndBadge_Mentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(sessionAttendanceResultRepository.findAttendedByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());

        PointsReconciliationReport report = reconciliationService.reconcileStudent(STUDENT_ID, MENTORSHIP_ID);

        assertThat(report.classification()).isEqualTo(ReconciliationClassification.PARTIAL);
        assertThat(report.unverifiedSubmissionsCount()).isEqualTo(1);
        assertThat(report.rationale()).contains("legacy graded submission(s) where points_applied was unpopulated");
    }

    @Test
    @DisplayName("Classifies as UNRECONSTRUCTABLE when submissions have null points_applied and null score (resubmitted/erased)")
    void reconcilesUnreconstructableWhenSubmissionHasNoScoreEvidence() {
        TotalPoints tp = TotalPoints.builder().totalPoints(100).build();
        when(totalPointsRepository.findByStudent_IdAndMentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Optional.of(tp));

        // Resubmitted task where finalScore was wiped to null and pointsApplied is null
        TaskSubmission resubmittedTask = TaskSubmission.builder().finalScore(null).pointsApplied(null).build();
        when(taskSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(List.of(resubmittedTask));
        when(projectSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(quizSubmissionRepository.findByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(badgeAwardRepository.findByStudent_IdAndBadge_Mentorship_Id(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());
        when(sessionAttendanceResultRepository.findAttendedByStudentIdAndMentorshipId(STUDENT_ID, MENTORSHIP_ID))
                .thenReturn(Collections.emptyList());

        PointsReconciliationReport report = reconciliationService.reconcileStudent(STUDENT_ID, MENTORSHIP_ID);

        assertThat(report.classification()).isEqualTo(ReconciliationClassification.UNRECONSTRUCTABLE);
        assertThat(report.unverifiedSubmissionsCount()).isEqualTo(1);
        assertThat(report.rationale()).contains("ambiguous or score was cleared on resubmission");
    }
}
