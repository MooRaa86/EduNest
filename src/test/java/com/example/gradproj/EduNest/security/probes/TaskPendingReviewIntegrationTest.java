package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.tasks.TaskServiceImpl;
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

class TaskPendingReviewIntegrationTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weeks;
    @Autowired private TaskRepository tasks;
    @Autowired private TaskSubmissionRepository submissions;
    @Autowired private TaskServiceImpl service;

    @Test
    void mixedStatusesAndPageSizesCountOnlySubmittedRowsForTask() {
        String owner = "eb54-review-mentor@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        var week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("week").build());
        var task = tasks.saveAndFlush(Task.builder().week(week).title("task").description("description")
                .points(10).passPoints(5).estimatedMinutes(10).dueAt(LocalDateTime.now().plusDays(1)).build());
        for (int i = 0; i < 3; i++) {
            var student = auth.enroll(chain.mentorship().getId(), "eb54-review-" + i + "@example.org").getStudent();
            submissions.saveAndFlush(TaskSubmission.builder().task(task).student(student)
                    .status(i == 2 ? SubmissionStatus.GRADED : SubmissionStatus.SUBMITTED).build());
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            assertThat(service.getTaskStatistics(task.getId(), PageRequest.of(0, 1), owner).getPendingReview()).isEqualTo(2);
            assertThat(service.getTaskStatistics(task.getId(), PageRequest.of(1, 2), owner).getPendingReview()).isEqualTo(2);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
