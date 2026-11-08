package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.tasks.TaskServiceImpl;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the actual JPQL update rather than modelling arithmetic in a mock. */
class TotalPointsAtomicH2IntegrationTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private TotalPointsRepository points;
    @Autowired private TotalPointsServiceImp service;
    @Autowired private WeekRepository weeks;
    @Autowired private TaskRepository tasks;
    @Autowired private TaskSubmissionRepository submissions;
    @Autowired private TaskServiceImpl taskService;

    @Test
    void existingBalanceIsIncrementedDecrementedAndFloored() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("points-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("points-student-" + suffix + "@example.org");
        points.saveAndFlush(TotalPoints.builder().student(student).mentorship(chain.mentorship())
                .totalPoints(10).build());

        service.applyDelta(student, chain.mentorship(), 5);
        assertThat(balance(student.getId(), chain.mentorship().getId())).isEqualTo(15);
        service.applyDelta(student, chain.mentorship(), -8);
        assertThat(balance(student.getId(), chain.mentorship().getId())).isEqualTo(7);
        service.applyDelta(student, chain.mentorship(), -20);
        assertThat(balance(student.getId(), chain.mentorship().getId())).isZero();
    }

    @Test
    void taskWithAmbiguousSubmissionRejectsPointsEditAndDeletion() {
        String suffix = UUID.randomUUID().toString();
        String owner = "freeze-mentor-" + suffix + "@example.org";
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(owner, null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MENTOR"))));
        var chain = auth.mentorship(owner, Status.ACTIVE);
        var student = auth.student("freeze-student-" + suffix + "@example.org");
        var week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("week").build());
        var task = tasks.saveAndFlush(Task.builder().week(week).title("task").description("description")
                .points(10).passPoints(5).estimatedMinutes(10).dueAt(LocalDateTime.now().plusDays(1)).build());
        PatchTaskRequest update = new PatchTaskRequest();
        update.setPoints(12);
        taskService.updateTask(task.getId(), update, null, owner);
        assertThat(tasks.findById(task.getId()).orElseThrow().getPoints()).isEqualTo(12);

        submissions.saveAndFlush(TaskSubmission.builder().task(task).student(student).build());
        update.setPoints(15);
        assertThatThrownBy(() -> taskService.updateTask(task.getId(), update, null, owner))
                .isInstanceOf(globalLogicEx.class);
        assertThatThrownBy(() -> taskService.deleteTask(task.getId(), owner))
                .isInstanceOf(globalLogicEx.class);
        assertThat(tasks.findById(task.getId()).orElseThrow().getPoints()).isEqualTo(12);
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private int balance(Long studentId, Long mentorshipId) {
        return points.findByStudent_IdAndMentorship_Id(studentId, mentorshipId)
                .orElseThrow().getTotalPoints();
    }
}
