package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EB-51 — Internal read-only reconciliation service.
 *
 * <p>Strict architectural constraints:
 * <ul>
 *   <li>No external REST API endpoints.</li>
 *   <li>No automatic repair or historical database writes.</li>
 *   <li>Classifies records strictly by reliable empirical evidence into:
 *       {@link ReconciliationClassification#EXACT},
 *       {@link ReconciliationClassification#PARTIAL}, or
 *       {@link ReconciliationClassification#UNRECONSTRUCTABLE}.</li>
 *   <li>Never estimates unknown historical contributions as exact.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointsReconciliationService {

    private final TotalPointsRepository totalPointsRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final BadgeAwardRepository badgeAwardRepository;
    private final SessionAttendanceResultRepository sessionAttendanceResultRepository;
    private final LiveSessionProperties liveSessionProperties;

    /**
     * Inspects a student's point-earning history within a mentorship against their TotalPoints balance.
     *
     * @param studentId the student ID
     * @param mentorshipId the mentorship ID
     * @return an immutable audit report
     */
    public PointsReconciliationReport reconcileStudent(Long studentId, Long mentorshipId) {
        TotalPoints tp = totalPointsRepository.findByStudent_IdAndMentorship_Id(studentId, mentorshipId).orElse(null);
        int ledgerTotalPoints = (tp != null) ? tp.getTotalPoints() : 0;

        int verifiedTasks = 0;
        int verifiedProjects = 0;
        int verifiedQuizzes = 0;
        int verifiedBadges = 0;
        int verifiedAttendance = 0;
        int unverifiedSubmissionsCount = 0;

        boolean hasLegacyGradedWithoutPointsApplied = false;
        boolean hasUnscoredOrResubmitted = false;

        // 1. Task submissions
        List<TaskSubmission> taskSubs = taskSubmissionRepository.findByStudentIdAndMentorshipId(studentId, mentorshipId);
        for (TaskSubmission s : taskSubs) {
            if (s.getPointsApplied() != null) {
                verifiedTasks += s.getPointsApplied();
            } else if (s.getFinalScore() != null) {
                unverifiedSubmissionsCount++;
                hasLegacyGradedWithoutPointsApplied = true;
            } else {
                unverifiedSubmissionsCount++;
                hasUnscoredOrResubmitted = true;
            }
        }

        // 2. Project submissions
        List<ProjectSubmission> projSubs = projectSubmissionRepository.findByStudentIdAndMentorshipId(studentId, mentorshipId);
        for (ProjectSubmission s : projSubs) {
            if (s.getPointsApplied() != null) {
                verifiedProjects += s.getPointsApplied();
            } else if (s.getFinalScore() != null) {
                unverifiedSubmissionsCount++;
                hasLegacyGradedWithoutPointsApplied = true;
            } else {
                unverifiedSubmissionsCount++;
                hasUnscoredOrResubmitted = true;
            }
        }

        // 3. Quiz submissions
        List<QuizSubmission> quizSubs = quizSubmissionRepository.findByStudentIdAndMentorshipId(studentId, mentorshipId);
        for (QuizSubmission s : quizSubs) {
            if (s.getScore() != null) {
                verifiedQuizzes += s.getScore().intValue();
            }
        }

        // 4. Badges
        List<BadgeAward> badgeAwards = badgeAwardRepository.findByStudent_IdAndBadge_Mentorship_Id(studentId, mentorshipId);
        for (BadgeAward b : badgeAwards) {
            if (b.getBadge() != null) {
                verifiedBadges += b.getBadge().getPoints();
            }
        }

        // 5. Attendance
        List<SessionAttendanceResult> attendanceResults = sessionAttendanceResultRepository
                .findAttendedByStudentIdAndMentorshipId(studentId, mentorshipId);
        int bonusPoints = (liveSessionProperties != null && liveSessionProperties.getAttendance() != null)
                ? liveSessionProperties.getAttendance().getBonusPoints() : 5;
        verifiedAttendance = attendanceResults.size() * bonusPoints;

        int totalVerifiable = verifiedTasks + verifiedProjects + verifiedQuizzes + verifiedBadges + verifiedAttendance;
        int discrepancy = ledgerTotalPoints - totalVerifiable;

        ReconciliationClassification classification;
        String rationale;

        if (hasUnscoredOrResubmitted) {
            classification = ReconciliationClassification.UNRECONSTRUCTABLE;
            rationale = "Student has " + unverifiedSubmissionsCount + " submission(s) where grading history is ambiguous or score was cleared on resubmission. Historical credit cannot be verified from row evidence.";
        } else if (hasLegacyGradedWithoutPointsApplied) {
            classification = ReconciliationClassification.PARTIAL;
            rationale = "Student has legacy graded submission(s) where points_applied was unpopulated. Verifiable ledger sum is incomplete.";
        } else if (discrepancy == 0) {
            classification = ReconciliationClassification.EXACT;
            rationale = "All submissions, quizzes, badges, and attendance records have complete recorded provenance and match the TotalPoints ledger balance exactly.";
        } else {
            classification = ReconciliationClassification.PARTIAL;
            rationale = "All items have recorded points, but an unexplained discrepancy of " + discrepancy + " points exists between the itemized sum and the TotalPoints ledger balance.";
        }

        return new PointsReconciliationReport(
                studentId,
                mentorshipId,
                ledgerTotalPoints,
                verifiedTasks,
                verifiedProjects,
                verifiedQuizzes,
                verifiedBadges,
                verifiedAttendance,
                totalVerifiable,
                unverifiedSubmissionsCount,
                classification,
                rationale
        );
    }
}
