package com.example.gradproj.EduNest.testing;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectAuthProjection;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectSubmissionAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskSubmissionAuthProjection;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.Tika;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * EB-5 / P0-07 — endpoint-level zip-spoof verification harness.
 *
 * Explicitly invoked evidence harness (name intentionally outside default Surefire
 * patterns): it refuses to run from the normal repository working directory. Uploads
 * run through REAL controllers/services and land under the test profile's
 * {@code target/test-uploads} — inside a disposable workspace outside the repository.
 * Retrieval is observed through the actual file endpoints (per-fixture authorized
 * projections via FileAccessService, plus the generic /files reader).
 */
class UploadZipEndpointHarness extends IntegrationTestBase {
    private static final Path ROOT;
    static {
        try {
            Path cwd = Path.of("").toRealPath();
            Path approved = Path.of(System.getProperty("java.io.tmpdir"), "opencode").toRealPath();
            Path marker = cwd.resolve(".eb5-isolated-workspace");
            if (!cwd.startsWith(approved) || cwd.equals(approved)
                    || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    || !Files.readString(marker).trim().equals(cwd.toString())
                    || Files.exists(cwd.resolve("uploads"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(cwd.resolve("target").resolve("test-uploads"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("EB-5 isolation guard failed: " + cwd);
            }
            ROOT = cwd.resolve("target").resolve("test-uploads").normalize();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired private ObjectMapper json;

    @MockBean private WeekRepository weeks;
    @MockBean private MentorShipRepository mentorships;
    @MockBean private TaskRepository taskRepository;
    @MockBean private TaskSubmissionRepository taskSubmissions;
    @MockBean private EnrollmentRepository enrollments;
    @MockBean private ProjectRepository projects;
    @MockBean private ProjectSubmissionRepository projectSubmissions;
    @MockBean private StudentRepository students;
    @MockBean private MentorRepository mentors;
    @MockBean private NotificationService notifications;

    private static final String EMAIL_M = "eb5-mentor@example.org";
    private static final String EMAIL_S = "eb5-student@example.org";

    private Mentor mentor;
    private Student student;
    private MentorShip mentorShip;
    private Week week;

    private static Set<Path> walkFiles(Path root) throws IOException {
        if (!Files.exists(root)) return Set.of();
        try (Stream<Path> stream = Files.walk(root)) {
            Set<Path> out = new TreeSet<>();
            stream.filter(Files::isRegularFile).forEach(out::add);
            return out;
        }
    }

    private MockHttpSession session(String email, String role) {
        var authentication = new UsernamePasswordAuthenticationToken(email, "fixture",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return session;
    }

    record Payload(String name, String filename, String declaredMime, byte[] bytes,
                   boolean acceptUnderPolicy, String note) {}

    record Surface(String name, String method, String endpoint, String role,
                   String responseKey, String pathField) {}

    private static final List<Surface> SURFACES = List.of(
            new Surface("task-attachment", "PATCH", "/api/v1/task/55", "MENTOR", "task", "uploadedAttachmentPath"),
            new Surface("project-attachment", "PATCH", "/api/v1/project/21", "MENTOR", "project", "uploadedAttachmentPath"),
            new Surface("task-submission", "POST", "/api/v1/task-submission/11", "STUDENT", "submission", "uploadedFilePath"),
            new Surface("project-submission", "POST", "/api/v1/project/21/submissions", "STUDENT", "submission", "uploadedFilePath"));

    @BeforeEach
    void fixtures() {
        mentor = mock(Mentor.class);
        student = mock(Student.class);
        mentorShip = mock(MentorShip.class);
        week = mock(Week.class);
        when(mentor.getId()).thenReturn(101L);
        when(mentor.getEmail()).thenReturn(EMAIL_M);
        when(student.getId()).thenReturn(102L);
        when(student.getEmail()).thenReturn(EMAIL_S);
        when(student.getFirstName()).thenReturn("Fixture");
        when(student.getLastName()).thenReturn("Student");
        when(mentorShip.getId()).thenReturn(701L);
        when(mentorShip.getTitle()).thenReturn("eb5-mentorship");
        when(mentorShip.getMentor()).thenReturn(mentor);
        when(week.getMentorship()).thenReturn(mentorShip);
        when(week.getId()).thenReturn(11L);
        when(week.getTitle()).thenReturn("eb5-week");

        when(weeks.findById(anyLong())).thenReturn(Optional.of(week));
        when(mentorships.existsByIdAndMentor_Email(anyLong(), anyString())).thenReturn(true);
        when(mentors.findByEmail(anyString())).thenReturn(Optional.of(mentor));
        when(students.findIdByEmail(anyString())).thenReturn(Optional.of(102L));
        when(students.getReferenceById(anyLong())).thenReturn(student);
        when(projects.getReferenceById(anyLong())).thenReturn(mock(Project.class));
        when(taskRepository.getReferenceById(anyLong())).thenReturn(mock(Task.class));
        when(enrollments.isStudentEnrolledForTask(anyLong(), anyLong())).thenReturn(true);
        when(enrollments.isStudentEnrolledForProjectByEmail(anyLong(), anyString())).thenReturn(true);
        when(taskSubmissions.findByTask_IdAndStudent_Id(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(projectSubmissions.findByProject_IdAndStudent_Id(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskSubmissions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectSubmissions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Fresh per-case entities so earlier accepted writes cannot leak into later response bodies. */
    private void resetAttachmentFixtures() {
        Task task = new Task();
        task.setTitle("eb5-task");
        task.setDescription("eb5 fixture task body");
        task.setPoints(100);
        task.setPassPoints(50);
        task.setEstimatedMinutes(60);
        task.setStatus(TaskStatus.PUBLISHED);
        task.setDueAt(LocalDateTime.now().plusDays(2));
        task.setWeek(week);
        when(taskRepository.findById(anyLong())).thenReturn(Optional.of(task));

        Project project = new Project();
        project.setTitle("eb5-project");
        project.setGoal("eb5 goal");
        project.setBrief("eb5 fixture project brief");
        project.setPoints(100);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setStartAt(LocalDateTime.now());
        project.setEndAt(LocalDateTime.now().plusDays(2));
        project.setWeek(week);
        when(projects.findById(anyLong())).thenReturn(Optional.of(project));
    }

    private void stubAuthorizedProjections(String filePath) {
        TaskAuthProjection taskAuth = mock(TaskAuthProjection.class);
        when(taskAuth.getMentorEmail()).thenReturn(EMAIL_M);
        when(taskAuth.getMentorshipId()).thenReturn(701L);
        when(taskAuth.getFilePath()).thenReturn(filePath);
        when(taskRepository.findAuthProjectionById(anyLong())).thenReturn(Optional.of(taskAuth));
        when(taskRepository.isStudentEnrolled(anyLong(), anyString())).thenReturn(true);

        ProjectAuthProjection projectAuth = mock(ProjectAuthProjection.class);
        when(projectAuth.getMentorEmail()).thenReturn(EMAIL_M);
        when(projectAuth.getMentorshipId()).thenReturn(701L);
        when(projectAuth.getFilePath()).thenReturn(filePath);
        when(projects.findAuthProjectionById(anyLong())).thenReturn(Optional.of(projectAuth));
        when(projects.isStudentEnrolled(anyLong(), anyString())).thenReturn(true);

        TaskSubmissionAuthProjection subAuth = mock(TaskSubmissionAuthProjection.class);
        when(subAuth.getStudentEmail()).thenReturn(EMAIL_S);
        when(subAuth.getMentorEmail()).thenReturn(EMAIL_M);
        when(subAuth.getFilePath()).thenReturn(filePath);
        when(taskSubmissions.findAuthProjectionById(anyLong())).thenReturn(Optional.of(subAuth));

        ProjectSubmissionAuthProjection psubAuth = mock(ProjectSubmissionAuthProjection.class);
        when(psubAuth.getStudentEmail()).thenReturn(EMAIL_S);
        when(psubAuth.getMentorEmail()).thenReturn(EMAIL_M);
        when(psubAuth.getFilePath()).thenReturn(filePath);
        when(projectSubmissions.findAuthProjectionById(anyLong())).thenReturn(Optional.of(psubAuth));
    }

    @Test
    void zipSpoofEndpointVerification() throws Exception {
        // ---- synthetic payloads ----
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipOut)) {
            zip.putNextEntry(new ZipEntry("inert-fixture.txt"));
            zip.write("inert fixture".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] realZip = zipOut.toByteArray();
        byte[] garbage = new byte[]{0, 1, 2, 3, 4, 5};
        byte[] html = "<html><body>inert fixture</body></html>".getBytes(StandardCharsets.UTF_8);

        List<Payload> payloads = List.of(
                new Payload("zip-valid", "fixture.zip", "application/zip", realZip, true, "genuine zip declared zip"),
                new Payload("zip-declared-octet", "fixture.zip", "application/octet-stream", realZip, true, "genuine zip declared octet-stream"),
                new Payload("binary-as-zip-octet", "fixture.zip", "application/octet-stream", garbage, false, "non-zip bytes detected octet-stream (FILE-04 core)"),
                new Payload("binary-as-zip-declared-zip", "fixture.zip", "application/zip", garbage, false, "non-zip bytes declared zip (FILE-04 variant)"),
                new Payload("html-as-zip", "fixture.zip", "application/octet-stream", html, false, "html detected text/html; blocked MIME"),
                new Payload("blocked-extension", "fixture.exe", "application/octet-stream", garbage, false, "rejection control .exe not whitelisted"),
                new Payload("empty", "fixture.zip", "application/octet-stream", new byte[0], false, "empty payload control"));

        Tika tika = new Tika();
        Map<String, Boolean> controlProbed = new HashMap<>();
        Map<String, Boolean> spoofProbed = new HashMap<>();
        int uploadObservations = 0;

        for (Surface surface : SURFACES) {
            int caseIndex = 0;
            for (Payload payload : payloads) {
                caseIndex++;
                resetAttachmentFixtures();
                Set<Path> before = walkFiles(ROOT);

                MockMultipartFile file = new MockMultipartFile("file", payload.filename(), payload.declaredMime(), payload.bytes());
                String principalEmail = "MENTOR".equals(surface.role()) ? EMAIL_M : EMAIL_S;
                MockHttpSession session = session(principalEmail, surface.role());
                MvcResult response = mockMvc.perform(multipart(surface.endpoint()).file(file)
                        .session(session)
                        .with(request -> {
                            request.setMethod(surface.method());
                            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(principalEmail, "fixture"));
                            return request;
                        }))
                        .andReturn();
                uploadObservations++;

                int status = response.getResponse().getStatus();
                JsonNode body = json.readTree(response.getResponse().getContentAsString());
                String storedPath = body.path("apiResponse").path(surface.responseKey()).path(surface.pathField()).asText("");

                Set<Path> after = walkFiles(ROOT);
                Set<Path> created = new TreeSet<>(after);
                created.removeAll(before);
                for (Path createdFile : created) {
                    assertTrue(createdFile.normalize().startsWith(ROOT), "storage escaped disposable root: " + createdFile);
                }

                boolean stored = !storedPath.isBlank()
                        && Files.isRegularFile(Path.of("").toRealPath().resolve(storedPath).normalize());
                assertEquals(stored, !created.isEmpty(),
                        "response/disk agreement on " + surface.name() + "/" + payload.name());

                String actualHash = "-";
                byte[] storedBytes = null;
                if (stored) {
                    assertEquals(1, created.size(), "single artifact per accepted upload: " + surface.name());
                    Path storedFile = Path.of("").toRealPath().resolve(storedPath).normalize();
                    assertTrue(storedFile.startsWith(ROOT), "response path escaped disposable root: " + storedPath);
                    storedBytes = Files.readAllBytes(storedFile);
                    actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(storedBytes));
                }

                boolean accepted = status >= 200 && status < 300 && stored;
                String verdict = accepted == payload.acceptUnderPolicy() ? "PASS" : "FAIL";

                String fetched = "-";
                String fetchedType = "-";
                String fetchedDisposition = "-";
                String fetchedHash = "-";
                if (stored) {
                    MvcResult getFile = mockMvc.perform(get("/files").param("filePath", storedPath)).andReturn();
                    fetched = Integer.toString(getFile.getResponse().getStatus());
                    fetchedType = String.valueOf(getFile.getResponse().getContentType());
                    fetchedDisposition = String.valueOf(getFile.getResponse().getHeader("Content-Disposition"));
                    byte[] served = getFile.getResponse().getContentAsByteArray();
                    fetchedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(served));
                    assertEquals("200", fetched, "generic /files GET status on " + surface.name() + "/" + payload.name());
                    assertTrue(served.length > 0, "generic /files GET returned no bytes on " + surface.name() + "/" + payload.name());
                    assertTrue(MessageDigest.isEqual(served, storedBytes),
                            "generic /files GET bytes differ from stored artifact on " + surface.name() + "/" + payload.name());
                }

                System.out.printf("EB5|%s|%s %s|%s|%s|%s|%s|%d|%s|%s|%s%n",
                        surface.name(), surface.method(), surface.endpoint(),
                        payload.name(), payload.filename(), payload.declaredMime(),
                        tika.detect(payload.bytes()), status,
                        accepted ? "ACCEPTED" : "REJECTED",
                        stored ? storedPath : "(none)", actualHash);
                System.out.printf("EB5RES|%s|%s|fetched=%s|ctype=%s|disposition=%s|fetchHashMatch=verified%n",
                        surface.name(), payload.name(), fetched, fetchedType, fetchedDisposition);

                if (caseIndex == 1) {
                    assertTrue(accepted, "fixture control failed on " + surface.name()
                            + " (HTTP " + status + "); wiring broken — surface results invalid");
                }

                // dedicated authorized retrieval probes: once for the genuine-ZIP control
                // artifact, once for the first accepted spoofed artifact (per surface),
                // each with byte-level content verification against the stored artifact
                boolean isSpoofPayload = payload.name().startsWith("binary-as-zip");
                boolean probeControl = stored && !controlProbed.containsKey(surface.name());
                boolean probeSpoof = isSpoofPayload && stored && !spoofProbed.containsKey(surface.name());
                if (probeControl) controlProbed.put(surface.name(), true);
                if (probeSpoof) spoofProbed.put(surface.name(), true);
                if (probeControl || probeSpoof) {
                    String kind = probeSpoof ? "spoof" : "control";
                    String probeEndpoint = switch (surface.name()) {
                        case "task-attachment" -> "/files/tasks/55/attachment";
                        case "project-attachment" -> "/files/projects/21/attachment";
                        case "task-submission" -> "/files/task-submissions/91";
                        default -> "/files/project-submissions/97";
                    };
                    // principal = the artifact owner identity as the fixtures model ownership
                    String ownerEmail = "MENTOR".equals(surface.role()) ? EMAIL_M : EMAIL_S;
                    String ownerRole = surface.role();
                    stubAuthorizedProjections(storedPath);
                    MvcResult authGet = mockMvc.perform(get(probeEndpoint)
                            .session(session(ownerEmail, ownerRole))
                            .with(request -> {
                                request.setUserPrincipal(new UsernamePasswordAuthenticationToken(ownerEmail, "fixture"));
                                return request;
                            }))
                            .andReturn();
                    int authStatus = authGet.getResponse().getStatus();
                    byte[] authBytes = authGet.getResponse().getContentAsByteArray();
                    String authHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(authBytes));
                    assertTrue(authStatus == 200, "authorized GET status on " + surface.name() + "/" + kind);
                    assertTrue(authBytes.length > 0, "authorized GET returned no bytes on " + surface.name() + "/" + kind);
                    assertTrue(MessageDigest.isEqual(authBytes, storedBytes),
                            "authorized GET bytes differ from stored artifact on " + surface.name() + "/" + kind);
                    System.out.printf("EB5AUTH|%s|%s|kind=%s|status=%d|ctype=%s|disposition=%s|bytes=%d|hashMatch=verified%n",
                            surface.name(), probeEndpoint, kind, authStatus,
                            String.valueOf(authGet.getResponse().getContentType()),
                            String.valueOf(authGet.getResponse().getHeader("Content-Disposition")), authBytes.length);
                }
            }
        }

        System.out.printf("EB5SUMMARY|surfaces=%d|payloads=7|uploadObservations=%d|authControlProbes=%d|authSpoofProbes=%d%n",
                SURFACES.size(), uploadObservations, controlProbed.size(), spoofProbed.size());
    }
}
