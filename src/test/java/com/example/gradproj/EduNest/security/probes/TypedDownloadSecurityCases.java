package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.util.FileResponseBuilder;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared tests run with separate relative and absolute disposable roots. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
abstract class TypedDownloadSecurityCases {
    private static final String OWNER = "typed-owner@example.org";
    private static final String ENROLLED = "typed-enrolled@example.org";
    private static final String STRANGER = "typed-stranger@example.org";
    private static final String MENTOR = "typed-mentor@example.org";
    private static final byte[] BYTES = "synthetic typed download content".getBytes(StandardCharsets.UTF_8);

    enum Kind { TASK_SUBMISSION, PROJECT_SUBMISSION, TASK_ATTACHMENT, PROJECT_ATTACHMENT }
    record Case(String url, String path) { }

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired FileStorageService storage;
    @Autowired WeekRepository weeks;
    @Autowired TaskRepository tasks;
    @Autowired TaskSubmissionRepository taskSubmissions;
    @Autowired ProjectRepository projects;
    @Autowired ProjectSubmissionRepository projectSubmissions;
    @Value("${app.upload.base-dir}") String root;
    @TempDir Path outside;
    private final List<Path> created = new ArrayList<>();

    @BeforeEach void users() {
        auth.student(OWNER);
        auth.student(ENROLLED);
        auth.student(STRANGER);
    }

    @AfterEach void cleanup() throws IOException {
        for (Path p : created) Files.deleteIfExists(p);
        created.clear();
    }

    private Case seed(Kind kind, String path) {
        return seed(kind, path, "PUBLISHED");
    }

    private Case seed(Kind kind, String path, String publicationStatus) {
        var ms = auth.mentorship(MENTOR, Status.ACTIVE).mentorship();
        auth.enroll(ms.getId(), OWNER);
        auth.enroll(ms.getId(), ENROLLED);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(ms).title("download fixture").build());
        if (kind == Kind.TASK_SUBMISSION || kind == Kind.TASK_ATTACHMENT) {
            Task task = tasks.saveAndFlush(Task.builder().week(week).title("fixture task")
                    .description("fixture").points(10).passPoints(5).estimatedMinutes(30)
                    .dueAt(LocalDateTime.now().plusDays(1)).status(TaskStatus.valueOf(publicationStatus))
                    .uploadedAttachmentPath(path).build());
            if (kind == Kind.TASK_ATTACHMENT) return new Case("/files/tasks/" + task.getId() + "/attachment", path);
            TaskSubmission sub = taskSubmissions.saveAndFlush(TaskSubmission.builder().task(task)
                    .student(auth.student(OWNER)).uploadedFilePath(path).submittedAt(LocalDateTime.now()).build());
            return new Case("/files/task-submissions/" + sub.getId(), path);
        }
        Project project = projects.saveAndFlush(Project.builder().week(week).title("fixture project")
                .brief("fixture").goal("fixture").points(10).startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(1)).status(ProjectStatus.valueOf(publicationStatus))
                .uploadedAttachmentPath(path).build());
        if (kind == Kind.PROJECT_ATTACHMENT) return new Case("/files/projects/" + project.getId() + "/attachment", path);
        ProjectSubmission sub = projectSubmissions.saveAndFlush(ProjectSubmission.builder().project(project)
                .student(auth.student(OWNER)).uploadedFilePath(path).submittedAt(LocalDateTime.now()).build());
        return new Case("/files/project-submissions/" + sub.getId(), path);
    }

    private Case stored(Kind kind) {
        return stored(kind, "PUBLISHED");
    }

    private Case stored(Kind kind, String publicationStatus) {
        String folder = switch (kind) {
            case TASK_SUBMISSION, PROJECT_SUBMISSION -> "submissions";
            case TASK_ATTACHMENT -> "task-attachment";
            case PROJECT_ATTACHMENT -> "project-attachment";
        };
        String path = storage.saveFile(folder, "fixture", 1L, 2L,
                new MockMultipartFile("file", "fixture.txt", "text/plain", BYTES));
        created.add(Path.of(path));
        return seed(kind, path, publicationStatus);
    }

    private String token(String email, String role) { return auth.authToken(email, role); }

    private static Stream<Arguments> attachmentStatusCases() {
        return Stream.of(
                Arguments.of(Kind.TASK_ATTACHMENT, TaskStatus.DRAFT.name(), false),
                Arguments.of(Kind.TASK_ATTACHMENT, TaskStatus.PUBLISHED.name(), true),
                Arguments.of(Kind.TASK_ATTACHMENT, TaskStatus.CLOSED.name(), true),
                Arguments.of(Kind.PROJECT_ATTACHMENT, ProjectStatus.DRAFT.name(), false),
                Arguments.of(Kind.PROJECT_ATTACHMENT, ProjectStatus.PUBLISHED.name(), true),
                Arguments.of(Kind.PROJECT_ATTACHMENT, ProjectStatus.CLOSED.name(), true));
    }

    @ParameterizedTest @MethodSource("attachmentStatusCases")
    void attachmentStatusControlsEnrolledStudentAccess(Kind kind, String publicationStatus, boolean visible) throws Exception {
        Case item = stored(kind, publicationStatus);
        mvc.perform(get(item.url()).header("Authorization", token(MENTOR, AuthFixture.MENTOR)))
                .andExpect(status().isOk()).andExpect(content().bytes(BYTES));

        var enrolled = mvc.perform(get(item.url()).header("Authorization", token(ENROLLED, AuthFixture.STUDENT)))
                .andReturn().getResponse();
        if (visible) {
            assertThat(enrolled.getStatus()).isEqualTo(200);
            assertThat(enrolled.getContentAsByteArray()).isEqualTo(BYTES);
        } else {
            assertThat(enrolled.getStatus()).isEqualTo(403);
            assertThat((String) JsonPath.read(enrolled.getContentAsString(), "$.errorCode")).isEqualTo("ERR_FORBIDDEN");
            assertThat(enrolled.getContentAsByteArray()).isNotEqualTo(BYTES);
        }

        var stranger = mvc.perform(get(item.url()).header("Authorization", token(STRANGER, AuthFixture.STUDENT)))
                .andReturn().getResponse();
        assertThat(stranger.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(stranger.getContentAsString(), "$.errorCode")).isEqualTo("ERR_FORBIDDEN");
        assertThat(stranger.getContentAsByteArray()).isNotEqualTo(BYTES);
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void actualStoredFileAuthorizationAndBytes(Kind kind) throws Exception {
        Case item = stored(kind);
        for (String email : List.of(OWNER, MENTOR)) {
            String role = email.equals(MENTOR) ? AuthFixture.MENTOR : AuthFixture.STUDENT;
            mvc.perform(get(item.url()).param("download", "true").header("Authorization", token(email, role)))
                    .andExpect(status().isOk()).andExpect(content().bytes(BYTES));
        }
        var enrolled = mvc.perform(get(item.url()).header("Authorization", token(ENROLLED, AuthFixture.STUDENT)))
                .andReturn().getResponse();
        if (kind == Kind.TASK_ATTACHMENT || kind == Kind.PROJECT_ATTACHMENT) {
            assertThat(enrolled.getStatus()).isEqualTo(200);
            assertThat(enrolled.getContentAsByteArray()).isEqualTo(BYTES);
        } else {
            assertThat(enrolled.getStatus()).isEqualTo(403);
            assertThat((String) JsonPath.read(enrolled.getContentAsString(), "$.errorCode")).isEqualTo("ERR_FORBIDDEN");
            assertThat(enrolled.getContentAsString())
                    .doesNotContain(new String(BYTES, StandardCharsets.UTF_8));
        }
        var stranger = mvc.perform(get(item.url()).header("Authorization", token(STRANGER, AuthFixture.STUDENT)))
                .andReturn().getResponse();
        assertThat(stranger.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(stranger.getContentAsString(), "$.errorCode")).isEqualTo("ERR_FORBIDDEN");
        assertThat(stranger.getContentAsString())
                .doesNotContain(new String(BYTES, StandardCharsets.UTF_8));
        mvc.perform(get(item.url())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void nonexistentResourceAndMissingFileAreSafe(Kind kind) throws Exception {
        Case item = seed(kind, "missing-" + UUID.randomUUID() + ".txt");
        mvc.perform(get(item.url()).header("Authorization", token(OWNER, AuthFixture.STUDENT)))
                .andExpect(status().isNotFound());
        String missing = switch (kind) {
            case TASK_SUBMISSION -> "/files/task-submissions/9223372036854775807";
            case PROJECT_SUBMISSION -> "/files/project-submissions/9223372036854775807";
            case TASK_ATTACHMENT -> "/files/tasks/9223372036854775807/attachment";
            case PROJECT_ATTACHMENT -> "/files/projects/9223372036854775807/attachment";
        };
        var response = mvc.perform(get(missing).header("Authorization", token(OWNER, AuthFixture.STUDENT)))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.errorCode")).isEqualTo("ERR_RESOURCE_NOT_FOUND");
        assertThat(response.getContentAsString()).doesNotContain("synthetic typed download content");
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void deniesBeforeOpeningUnderlyingFile(Kind kind) throws Exception {
        Case item = stored(kind);
        try (MockedStatic<FileResponseBuilder> responseBuilder = mockStatic(FileResponseBuilder.class)) {
            var response = mvc.perform(get(item.url()).header("Authorization", token(STRANGER, AuthFixture.STUDENT)))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat((String) JsonPath.read(response.getContentAsString(), "$.errorCode")).isEqualTo("ERR_FORBIDDEN");
            assertThat(response.getContentAsString())
                    .doesNotContain(new String(BYTES, StandardCharsets.UTF_8));
            responseBuilder.verify(() -> FileResponseBuilder.buildResponse(any(Path.class), anyBoolean()), never());
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void traversalIsRejected(Kind kind) throws Exception {
        Case item = seed(kind, "../outside-" + UUID.randomUUID() + ".txt");
        mvc.perform(get(item.url()).header("Authorization", token(OWNER, AuthFixture.STUDENT)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void storedPathSeparatorsAndDotSegmentsNormalize(Kind kind) throws Exception {
        Case original = stored(kind);
        Path path = Path.of(original.path());
        String dotted = path.getParent().resolve(".").resolve(path.getFileName()).toString();
        Case item = seed(kind, dotted.replace('/', '\\'));
        mvc.perform(get(item.url()).header("Authorization", token(OWNER, AuthFixture.STUDENT)))
                .andExpect(status().isOk()).andExpect(content().bytes(BYTES));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void absolutePathOutsideRootIsRejected(Kind kind) throws Exception {
        Path secret = Files.writeString(outside.resolve("outside.txt"), "synthetic outside secret");
        Case item = seed(kind, secret.toAbsolutePath().toString());
        mvc.perform(get(item.url()).header("Authorization", token(OWNER, AuthFixture.STUDENT)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void symbolicLinkCannotEscapeRoot(Kind kind) throws Exception {
        Files.writeString(outside.resolve("outside.txt"), "synthetic outside secret");
        Path link = Path.of(root).toAbsolutePath().resolve("link-" + UUID.randomUUID());
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
                assumeTrue(false, "Creating symbolic links is unavailable: " + e.getMessage());
            }
            // A directory junction is a Windows reparse point and does not require
            // symlink privileges. Both its location and destination are disposable.
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                    link.toString(), outside.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assumeTrue(process.waitFor() == 0, "Cannot create disposable junction: " + output);
        }
        created.add(link);
        Case item = seed(kind, link.getFileName() + "/outside.txt");
        var response = mvc.perform(get(item.url()).header("Authorization", token(OWNER, AuthFixture.STUDENT)))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isBetween(400, 499);
        assertThat(response.getContentAsString()).doesNotContain("synthetic outside secret");
    }
}
