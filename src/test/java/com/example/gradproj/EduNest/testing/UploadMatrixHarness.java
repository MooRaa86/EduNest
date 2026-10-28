package com.example.gradproj.EduNest.testing;

import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Explicitly invoked evidence harness, never run from a normal project working directory. */
class UploadMatrixHarness extends IntegrationTestBase {
    private static final Path ROOT;
    static {
        try {
            Path cwd = Path.of("").toRealPath();
            Path approved = Path.of(System.getProperty("java.io.tmpdir"), "opencode").toRealPath();
            Path marker = cwd.resolve(".eb4-isolated-workspace");
            if (!cwd.startsWith(approved) || cwd.equals(approved)
                    || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    || !Files.readString(marker).trim().equals(cwd.toString())
                    || Files.exists(cwd.resolve("uploads"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("EB-4 isolation guard failed: " + cwd);
            }
            ROOT = cwd.resolve("uploads").normalize();
            System.out.println("EB4_ISOLATED_CWD=" + cwd + " EB4_UPLOAD_ROOT=" + ROOT);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired private ObjectMapper json;
    @Autowired private FileStorageService files;
    @MockBean private MentorShipRepository mentorships;
    @MockBean private StudentRepository students;
    @MockBean private MentorRepository mentors;
    @MockBean private UserRepository users;
    @MockBean private ChatRoomRepository rooms;

    private static final String EMAIL = "eb4-fixture@example.org";

    @BeforeEach
    void fixtures() {
        Mentor mentor = mock(Mentor.class);
        Student student = mock(Student.class);
        MentorShip mentorship = mock(MentorShip.class);
        ChatRoom room = mock(ChatRoom.class);
        when(mentor.getEmail()).thenReturn(EMAIL);
        when(mentor.getId()).thenReturn(101L);
        when(student.getId()).thenReturn(102L);
        when(mentorship.getMentor()).thenReturn(mentor);
        when(room.getCreator()).thenReturn(mentor);
        when(room.getId()).thenReturn(103L);
        when(mentorships.findById(anyLong())).thenReturn(Optional.of(mentorship));
        when(students.findByEmail(anyString())).thenReturn(Optional.of(student));
        when(mentors.findByEmail(anyString())).thenReturn(Optional.of(mentor));
        when(users.findByEmail(anyString())).thenReturn(Optional.of(mentor));
        when(rooms.findRoomById(anyLong())).thenReturn(Optional.of(room));
    }

    record Surface(String name, String method, String endpoint, String role, String responseKey) {}
    record Payload(String name, String filename, String mime, String actual, byte[] bytes, boolean secureAccept) {}

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void fourSurfaceMatrixAndZipContrast() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpg", out));
        byte[] jpeg = out.toByteArray();
        byte[] html = "<html><body>inert fixture</body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] garbage = new byte[]{0, 1, 2, 3, 4, 5};
        List<Payload> payloads = List.of(
                new Payload("valid-jpeg", "photo.jpg", "image/jpeg", "JPEG", jpeg, true),
                new Payload("wrong-extension", "photo.exe", "image/jpeg", "JPEG", jpeg, false),
                new Payload("spoofed-mime", "photo.jpg", "image/png", "JPEG", jpeg, false),
                new Payload("html-as-image", "photo.jpg", "image/jpeg", "HTML", html, false),
                new Payload("double-extension", "photo.html.jpg", "image/jpeg", "HTML", html, false),
                new Payload("malformed-image", "photo.png", "image/png", "garbage", garbage, false),
                new Payload("empty", "photo.jpg", "image/jpeg", "empty", new byte[0], false));
        List<Surface> surfaces = List.of(
                new Surface("cover", "POST", "/api/v1/mentorship/101/change-cover-image", "MENTOR", "Image_URL"),
                new Surface("student", "PATCH", "/student/profile/image", "STUDENT", "profileImageUrl"),
                new Surface("mentor", "PATCH", "/mentor/profile/image", "MENTOR", "profileImageUrl"),
                new Surface("chatroom", "PUT", "/api/v1/chat-room/103/image", "MENTOR", "imageUrl"));

        for (Surface surface : surfaces) {
            for (Payload payload : payloads) {
                var authentication = new UsernamePasswordAuthenticationToken(EMAIL, "fixture",
                        List.of(new SimpleGrantedAuthority("ROLE_" + surface.role())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                MockHttpSession session = new MockHttpSession();
                session.setAttribute("SPRING_SECURITY_CONTEXT", context);
                MockMultipartFile file = new MockMultipartFile("image", payload.filename(), payload.mime(), payload.bytes());
                MvcResult response = mockMvc.perform(multipart(surface.endpoint()).file(file)
                        .session(session).principal(authentication)
                        .with(request -> { request.setMethod(surface.method()); return request; })).andReturn();
                int status = response.getResponse().getStatus();
                JsonNode body = json.readTree(response.getResponse().getContentAsString());
                String url = body.path("apiResponse").path(surface.responseKey()).asText("");
                boolean stored = !url.isBlank() && Files.isRegularFile(Path.of("").toRealPath().resolve(url.substring(1)).normalize());
                String actualHash = "-", served = "-", servedMime = "-", disposition = "-", nosniff = "-", csp = "-";
                if (stored) {
                    Path saved = Path.of("").toRealPath().resolve(url.substring(1)).normalize();
                    assertTrue(saved.startsWith(ROOT), "storage escaped disposable uploads root");
                    actualHash = hash(Files.readAllBytes(saved));
                    MvcResult get = mockMvc.perform(get(url)).andReturn();
                    served = Integer.toString(get.getResponse().getStatus());
                    servedMime = String.valueOf(get.getResponse().getContentType());
                    disposition = String.valueOf(get.getResponse().getHeader("Content-Disposition"));
                    nosniff = String.valueOf(get.getResponse().getHeader("X-Content-Type-Options"));
                    csp = String.valueOf(get.getResponse().getHeader("Content-Security-Policy"));
                }
                String verdict = (status >= 200 && status < 300) == payload.secureAccept() ? "PASS" : "FAIL";
                System.out.printf("EB4|%s|%s|%s|%s|%s|%s|%d|%s|%s|%s|%s|%s|%s|%s|%s|%s%n",
                        surface.name(), surface.endpoint(), payload.name(), payload.filename(), payload.mime(), payload.actual(),
                        status, stored, url, actualHash, served, servedMime, disposition, nosniff, csp, verdict);
            }
        }

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
            zip.putNextEntry(new ZipEntry("fixture.txt"));
            zip.write("inert fixture".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<Payload> zipCases = List.of(
                new Payload("zip-valid", "fixture.zip", "application/zip", "ZIP", zipBytes.toByteArray(), true),
                new Payload("zip-wrong-extension", "fixture.pdf", "application/pdf", "ZIP", zipBytes.toByteArray(), false),
                new Payload("binary-as-zip", "fixture.zip", "application/octet-stream", "binary", garbage, false));
        for (String[] destination : List.of(
                new String[]{"task-submission", "submissions", "task"},
                new String[]{"project-submission", "submissions", "project"},
                new String[]{"task-attachment", "task-attachment", "task"},
                new String[]{"project-attachment", "project-attachment", "project"})) {
          for (Payload payload : zipCases) {
            String detected = new Tika().detect(payload.bytes());
            try {
                String path = files.saveFile(destination[1], destination[2], 10L, 20L,
                        new MockMultipartFile("file", payload.filename(), payload.mime(), payload.bytes()));
                Path stored = Path.of(path).toAbsolutePath().normalize();
                assertTrue(stored.startsWith(Path.of("").toRealPath()), "attachment escaped sandbox");
                MvcResult get = mockMvc.perform(get("/files").param("filePath", path)).andReturn();
                System.out.printf("EB4ZIP|%s|%s|%s|%s|%s|%s|ACCEPTED|%s|%s|%d|%s|%s|%s|%s%n", destination[0], payload.name(), payload.filename(), payload.mime(), payload.actual(), detected, path, hash(Files.readAllBytes(stored)), get.getResponse().getStatus(), get.getResponse().getContentType(), get.getResponse().getHeader("Content-Disposition"), get.getResponse().getHeader("X-Content-Type-Options"), get.getResponse().getHeader("Content-Security-Policy"));
            } catch (IllegalArgumentException | SecurityException e) {
                System.out.printf("EB4ZIP|%s|%s|%s|%s|%s|%s|REJECTED|%s|-|-|-|-|-|-%n", destination[0], payload.name(), payload.filename(), payload.mime(), payload.actual(), detected, e.getClass().getSimpleName());
            }
          }
        }
    }
}
