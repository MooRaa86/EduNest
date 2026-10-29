package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskStudentSubmissionIdTest extends ProdChainIntegrationTestBase {
    private static final String STUDENT = "task-id-student@example.org";
    private static final String OTHER = "task-id-other@example.org";
    private static final String NO_SUBMISSION = "task-id-no-submission@example.org";

    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weeks;
    @Autowired private TaskRepository tasks;
    @Autowired private TaskSubmissionRepository submissions;
    @Autowired private ObjectMapper mapper;

    private Task task;
    private long studentSubmissionId;
    private long otherSubmissionId;

    @BeforeEach
    void seed() {
        var mentorship = auth.mentorship("task-id-mentor@example.org", Status.ACTIVE).mentorship();
        Week week = weeks.saveAndFlush(Week.builder().mentorship(mentorship).title("task id week").build());
        task = tasks.saveAndFlush(Task.builder().week(week).title("task id fixture")
                .description("existing description").points(10).passPoints(5).estimatedMinutes(30)
                .dueAt(LocalDateTime.now().plusDays(1)).status(TaskStatus.PUBLISHED).build());
        studentSubmissionId = submission(STUDENT);
        otherSubmissionId = submission(OTHER);
        auth.student(NO_SUBMISSION);
    }

    private long submission(String email) {
        return submissions.saveAndFlush(TaskSubmission.builder().task(task).student(auth.student(email))
                .fileUrl("https://example.org/submission").status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now()).build()).getId();
    }

    private JsonNode taskDetail(String email) throws Exception {
        String json = mockMvc.perform(get("/api/v1/task/{taskId}/student", task.getId())
                        .header("Authorization", auth.authToken(email, AuthFixture.STUDENT)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).path("apiResponse").path("data");
    }

    @Test
    void returnsOnlyTheCurrentStudentsSubmissionId() throws Exception {
        JsonNode student = taskDetail(STUDENT);
        JsonNode other = taskDetail(OTHER);
        assertThat(student.path("submissionId").asLong()).isEqualTo(studentSubmissionId);
        assertThat(other.path("submissionId").asLong()).isEqualTo(otherSubmissionId);
        assertThat(studentSubmissionId).isNotEqualTo(otherSubmissionId);
        assertThat(student.path("taskId").asLong()).isEqualTo(task.getId());
        assertThat(student.path("description").asText()).isEqualTo("existing description");
        assertThat(student.path("submissionUrl").asText()).isEqualTo("https://example.org/submission");
    }

    @Test
    void returnsExplicitNullWhenCurrentStudentHasNoSubmission() throws Exception {
        JsonNode data = taskDetail(NO_SUBMISSION);
        assertThat(data.has("submissionId")).isTrue();
        assertThat(data.get("submissionId").isNull()).isTrue();
        assertThat(data.path("taskId").asLong()).isEqualTo(task.getId());
    }
}
