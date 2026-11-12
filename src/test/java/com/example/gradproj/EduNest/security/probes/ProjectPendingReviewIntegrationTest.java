package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.projects.ProjectServiceImpl;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EB-54 / P4-05 — project statistics pendingReview semantics.
 *
 * pendingReview counts project submission rows still awaiting review
 * (status SUBMITTED) — mirroring the task-side rule asserted by
 * TaskPendingReviewIntegrationTest. An enrolled student who never
 * submitted is "awaiting submission", not pending review, and a GRADED
 * row has left the review queue. The count must be independent of the
 * requested submission page.
 */
class ProjectPendingReviewIntegrationTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weeks;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectSubmissionRepository submissions;
    @Autowired private ProjectServiceImpl service;

    private Project newProject(com.example.gradproj.EduNest.entity.mentorship.MentorShip ms) {
        Week week = weeks.saveAndFlush(Week.builder().mentorship(ms).title("week").build());
        return projects.saveAndFlush(Project.builder()
                .week(week).title("project").brief("brief").goal("goal")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .points(10)
                .build());
    }

    private ProjectSubmission submission(Project project, com.example.gradproj.EduNest.entity.users.Student student,
                                         SubmissionStatus status) {
        return submissions.saveAndFlush(ProjectSubmission.builder()
                .project(project).student(student).status(status)
                .submittedAt(LocalDateTime.now()).build());
    }

    @Test
    void countsOnlySubmittedRowsRegardlessOfSubmissionPage() {
        String owner = "eb54-proj-review-mentor@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        Project project = newProject(chain.mentorship());

        // three enrolled students: one never submits, one SUBMITTED, one GRADED
        var a = auth.enroll(chain.mentorship().getId(), "eb54-proj-a@example.org").getStudent();
        var b = auth.enroll(chain.mentorship().getId(), "eb54-proj-b@example.org").getStudent();
        var c = auth.enroll(chain.mentorship().getId(), "eb54-proj-c@example.org").getStudent();
        submission(project, b, SubmissionStatus.SUBMITTED);
        submission(project, c, SubmissionStatus.GRADED);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            var statsPage0 = service.getProjectStatistics(project.getId(), PageRequest.of(0, 1));
            var statsPage1 = service.getProjectStatistics(project.getId(), PageRequest.of(1, 1));
            assertThat(statsPage0.getPendingReview()).isEqualTo(1);
            assertThat(statsPage1.getPendingReview()).isEqualTo(1);
            // totalSubmissions semantics unchanged: rows of the project (2)
            assertThat(statsPage0.getTotalSubmissions()).isEqualTo(2);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resubmissionReturnsRowToTheReviewQueue() {
        String owner = "eb54-proj-resub-mentor@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        Project project = newProject(chain.mentorship());
        var student = auth.enroll(chain.mentorship().getId(), "eb54-proj-d@example.org").getStudent();
        ProjectSubmission row = submission(project, student, SubmissionStatus.SUBMITTED);

        assertThat(submissions.countByProject_IdAndStatus(project.getId(), SubmissionStatus.SUBMITTED)).isEqualTo(1);
        row.setStatus(SubmissionStatus.GRADED);
        submissions.saveAndFlush(row);
        assertThat(submissions.countByProject_IdAndStatus(project.getId(), SubmissionStatus.SUBMITTED)).isEqualTo(0);
        // resubmission mutates the same (project, student) row back to SUBMITTED
        row.setStatus(SubmissionStatus.SUBMITTED);
        submissions.saveAndFlush(row);
        assertThat(submissions.countByProject_IdAndStatus(project.getId(), SubmissionStatus.SUBMITTED)).isEqualTo(1);
    }

    @Test
    void emptyProjectHasZeroPendingReview() {
        String owner = "eb54-proj-empty-mentor@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        Project project = newProject(chain.mentorship());
        assertThat(submissions.countByProject_IdAndStatus(project.getId(), SubmissionStatus.SUBMITTED)).isZero();
    }
}
