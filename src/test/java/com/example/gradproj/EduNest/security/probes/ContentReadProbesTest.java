package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.tasks.TaskService;
import com.example.gradproj.EduNest.service.lecture.LectureService;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import com.example.gradproj.EduNest.service.projects.ProjectService;
import com.example.gradproj.EduNest.service.projects.ProjectSubmissionService;
import com.example.gradproj.EduNest.service.week.StudentWeekService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("critical")
class ContentReadProbesTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weeks;
    @Autowired private LectureRepository lectures;
    @Autowired private TaskRepository tasks;
    @Autowired private ProjectRepository projects;
    @Autowired private TaskService taskService;
    @Autowired private LectureService lectureService;
    @Autowired private mentorShipService mentorService;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectSubmissionService projectSubmissionService;
    @Autowired private StudentWeekService studentWeekService;
    @Autowired private StudentRepository students;
    @Autowired private ObjectMapper objectMapper;
    private static final String OWNER = "eb21-owner@example.org", STUDENT = "eb21-student@example.org";
    private static final String OTHER = "eb21-other@example.org", FOREIGN = "eb21-foreign@example.org";
    private static final String MENTOR = "eb21-mentor-other@example.org", ADMIN = "eb21-admin@example.org";
    private long mentorshipId, weekId, lectureId, taskId, projectId, draftTaskId, draftProjectId;

    @BeforeEach void seed() {
        var course = auth.mentorship(OWNER, Status.ACTIVE).mentorship();
        mentorshipId = course.getId();
        var second = auth.mentorship(MENTOR, Status.ACTIVE).mentorship();
        auth.enroll(mentorshipId, STUDENT);
        auth.enroll(second.getId(), FOREIGN);
        auth.student(OTHER);
        auth.admin(ADMIN);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(course).title("eb21 week").build());
        weekId = week.getId();
        lectureId = lectures.saveAndFlush(Lecture.builder().week(week).title("eb21-secret").lectureUrl("https://example.org/secret").build()).getId();
        taskId = task(week, TaskStatus.PUBLISHED);
        draftTaskId = task(week, TaskStatus.DRAFT);
        projectId = project(week, ProjectStatus.PUBLISHED);
        draftProjectId = project(week, ProjectStatus.DRAFT);
    }

    private long task(Week week, TaskStatus state) {
        return tasks.saveAndFlush(Task.builder().week(week).title("eb21-secret-" + state).description("eb21-confidential")
                .points(10).passPoints(5).estimatedMinutes(30).dueAt(LocalDateTime.now().plusDays(1)).status(state).build()).getId();
    }
    private long project(Week week, ProjectStatus state) {
        return projects.saveAndFlush(Project.builder().week(week).title("eb21-secret-" + state).brief("eb21-confidential")
                .goal("fixture").points(10).startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusDays(1)).status(state).build()).getId();
    }
    private List<String> routes() {
        return List.of("/lectures/" + lectureId, "/lectures/week/" + weekId, "/lectures/mentorship/" + mentorshipId,
                "/api/v1/mentorship/" + mentorshipId + "/get-tasks", "/api/v1/task/" + taskId,
                "/api/v1/task/filter/" + mentorshipId, "/api/v1/task/" + taskId + "/student",
                "/api/v1/project/" + projectId, "/api/v1/project/filter/" + mentorshipId,
                "/api/v1/project/" + projectId + "/student");
    }
    @Test void fullRoleAndCrossCourseMatrix() throws Exception {
        for (String route : routes()) {
            mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
            for (String email : List.of(OTHER, FOREIGN)) {
                String response = mockMvc.perform(get(route).header("Authorization", auth.authToken(email, AuthFixture.STUDENT)))
                        .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
                assertThat(response).doesNotContain("eb21-secret", "eb21-confidential");
            }
            mockMvc.perform(get(route).header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR))).andExpect(status().isForbidden());
            for (String[] principal : new String[][]{{STUDENT, AuthFixture.STUDENT}, {OWNER, AuthFixture.MENTOR}, {ADMIN, AuthFixture.ADMIN}}) {
                mockMvc.perform(get(route).header("Authorization", auth.authToken(principal[0], principal[1]))).andExpect(status().isOk());
            }
        }
    }
    @Test void draftContentIsHiddenFromStudentsIncludingFilters() throws Exception {
        for (String route : List.of("/api/v1/task/" + draftTaskId, "/api/v1/task/" + draftTaskId + "/student",
                "/api/v1/project/" + draftProjectId, "/api/v1/project/" + draftProjectId + "/student")) {
            mockMvc.perform(get(route).header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT))).andExpect(status().isForbidden());
            mockMvc.perform(get(route).header("Authorization", auth.authToken(OWNER, AuthFixture.MENTOR))).andExpect(status().isOk());
        }
        for (String route : List.of("/api/v1/mentorship/" + mentorshipId + "/get-tasks", "/api/v1/task/filter/" + mentorshipId,
                "/api/v1/project/filter/" + mentorshipId)) {
            String body = mockMvc.perform(get(route).header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("eb21-secret-DRAFT").contains("eb21-secret-PUBLISHED");
        }
    }
    @Test void missingResourcesDenyWithoutDisclosure() throws Exception {
        for (String route : List.of("/lectures/999999999", "/lectures/week/999999999", "/lectures/mentorship/999999999",
                "/api/v1/mentorship/999999999/get-tasks", "/api/v1/task/999999999", "/api/v1/task/filter/999999999",
                "/api/v1/task/999999999/student", "/api/v1/project/999999999", "/api/v1/project/filter/999999999",
                "/api/v1/project/999999999/student")) {
            mockMvc.perform(get(route).header("Authorization", auth.authToken(OTHER, AuthFixture.STUDENT))).andExpect(status().isForbidden());
        }
    }
    @Test void directServiceCannotSpoofAnotherStudent() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(OTHER, "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        try {
            assertThatThrownBy(() -> taskService.getTaskWithSubmission(taskId, STUDENT)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> taskService.getTaskById(taskId)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> taskService.getTasks(null, null, mentorshipId, PageRequest.of(0, 4))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> lectureService.getLectureById(lectureId)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> lectureService.getLecturesByWeekId(weekId)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> lectureService.getLecturesByMentorshipId(mentorshipId, 0, 4)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> mentorService.getMentorShipTasks(mentorshipId)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> projectService.getProjectById(projectId)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> projectService.getProject(null, null, mentorshipId, PageRequest.of(0, 4))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> projectSubmissionService.getProjectWithSubmission(projectId, STUDENT)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
        assertThatThrownBy(() -> taskService.getTaskById(taskId)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void unrelatedMentorWhoIsAlsoEnrolledDoesNotGainDraftAccess() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(STUDENT, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            assertThatThrownBy(() -> taskService.getTaskById(draftTaskId)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> projectService.getProjectById(draftProjectId)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void studentCurriculumProductionChainAndResponseContract() throws Exception {
        String token = auth.authToken(STUDENT, AuthFixture.STUDENT);
        for (String route : List.of("/api/v1/student/week/" + mentorshipId + "/weeks",
                "/api/v1/student/week/" + mentorshipId + "/weeks-with-contents",
                "/api/v1/student/week/" + weekId + "/contents")) {
            mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
            for (String email : List.of(OTHER, FOREIGN)) {
                mockMvc.perform(get(route).header("Authorization", auth.authToken(email, AuthFixture.STUDENT)))
                        .andExpect(status().isForbidden());
            }
            JsonNode api = objectMapper.readTree(mockMvc.perform(get(route).header("Authorization", token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("apiResponse");
            assertThat(api.path("status").asText()).isNotBlank();
            if (route.endsWith("/weeks")) {
                assertThat(api.path("weeks").path("mentorshipTitle").asText()).isNotBlank();
                assertThat(api.path("weeks").path("weeks").get(0).path("id").asLong()).isEqualTo(weekId);
                assertThat(api.path("weeks").path("weeks").get(0).path("title").asText()).isEqualTo("eb21 week");
            } else {
                JsonNode week = route.endsWith("/contents") && !route.endsWith("/weeks-with-contents")
                        ? api.path("week") : api.path("weeks").path("weeks").get(0);
                assertThat(week.path("weekId").asLong()).isEqualTo(weekId);
                assertThat(week.path("weekTitle").asText()).isEqualTo("eb21 week");
                JsonNode items = week.path("items");
                assertThat(items.isArray()).isTrue();
                assertThat(items.toString()).contains("eb21-secret-PUBLISHED", "eb21-secret")
                        .doesNotContain("eb21-secret-DRAFT");
                assertThat(items.findValuesAsText("type")).contains("LECTURE", "TASK", "PROJECT");
            }
        }
    }

    @Test void studentCurriculumDirectServiceUsesPrincipalNotSuppliedEmail() {
        for (String caller : List.of(OTHER, FOREIGN)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller, "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
            try {
                assertThatThrownBy(() -> studentWeekService.getWeeksByMentorship(mentorshipId, STUDENT)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> studentWeekService.getMentorshipWeeksWithContents(mentorshipId, STUDENT)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> studentWeekService.getWeekContents(weekId)).isInstanceOf(AccessDeniedException.class);
            } finally { SecurityContextHolder.clearContext(); }
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(STUDENT, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        try {
            assertThat(studentWeekService.getWeeksByMentorship(mentorshipId, OTHER).getWeeks()).hasSize(1);
            assertThat(studentWeekService.getMentorshipWeeksWithContents(mentorshipId, OTHER).getWeeks()).hasSize(1);
            assertThat(studentWeekService.getWeekContents(weekId).getWeekId()).isEqualTo(weekId);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void deactivatedAccountCannotUseExistingJwtForCurriculum() throws Exception {
        String token = auth.authToken(STUDENT, AuthFixture.STUDENT);
        var student = students.findByEmail(STUDENT).orElseThrow();
        student.setEnabled(false);
        students.saveAndFlush(student);
        try {
            mockMvc.perform(get("/api/v1/student/week/" + mentorshipId + "/weeks-with-contents")
                    .header("Authorization", token)).andExpect(status().isUnauthorized());
        } finally {
            student.setEnabled(true);
            students.saveAndFlush(student);
        }
    }
}
