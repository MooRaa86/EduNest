package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.config.transaction.AfterCommitExecutor;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.weeks.CreateWeekrequest;
import com.example.gradproj.EduNest.dto.weeks.WeekResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import com.example.gradproj.EduNest.service.tasks.TaskService;
import com.example.gradproj.EduNest.service.week.WeekService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3-03 / EB-45: Transactional Coverage + File-Op Atomicity Probes Test.
 *
 * Covers:
 * 1. DB-05: Transactional boundary & atomicity on WeekService (createWeek, deleteWeek cascade rollback).
 * 2. FILE-05: Old files deleted AFTER commit (no premature file deletion when tx rolls back).
 * 3. FILE-05: Successful commit executes afterCommit hook and removes obsolete files from storage.
 * 4. FILE-05: Task update with invalid validation (passPoints > points) aborts without deleting existing attachment.
 * 5. DRY: Centralized AfterCommitExecutor executes immediately outside tx, defers within tx, and suppresses on rollback.
 */
@Tag("critical")
@Tag("transactional-coverage")
public class TransactionalCoverageAndFileAtomicityProbesTest extends ProdChainIntegrationTestBase {

    @Autowired private AuthFixture auth;
    @Autowired private WeekService weekService;
    @Autowired private mentorShipService mentorShipService;
    @Autowired private TaskService taskService;
    @Autowired private WeekRepository weekRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MentorShipRepository mentorShipRepository;
    @Autowired private AfterCommitExecutor afterCommitExecutor;
    @Autowired private PlatformTransactionManager txManager;

    @Value("${app.upload.base-dir}")
    private String baseDir;

    private final List<Path> cleanupPaths = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        for (Path p : cleanupPaths) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {}
        }
        cleanupPaths.clear();
    }

    private void authenticateAsMentor(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
        );
    }

    private static byte[] samplePngBytes() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static final byte[] VALID_PDF = "%PDF-1.4\n1 0 obj\n<<\n>>\nendobj\ntrailer\n<<\n>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("DB-05: deleteWeek with cascade rolls back atomically when exception occurs")
    void db05_deleteWeekRollbackPreservesWeekAndChildEntities() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "db05-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        authenticateAsMentor(mentorEmail);

        // Create week via service
        CreateWeekrequest createReq = new CreateWeekrequest();
        createReq.setMentorshipId(chain.mentorship().getId());
        createReq.setTitle("Week 1 - " + suffix);
        WeekResponse weekResp = weekService.createWeek(createReq);
        Long weekId = weekResp.getId();

        // Create a child task under week
        Week week = weekRepository.findById(weekId).orElseThrow();
        Task task = Task.builder()
                .title("Task in Week 1")
                .description("Sample task")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.DRAFT)
                .dueAt(java.time.LocalDateTime.now().plusDays(7))
                .week(week)
                .build();
        task = taskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        assertThat(weekRepository.existsById(weekId)).isTrue();
        assertThat(taskRepository.existsById(taskId)).isTrue();

        // Execute deleteWeek inside a rolling-back transaction template
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tx.execute(status -> {
            weekService.deleteWeek(weekId);
            // Simulate mid-operation or post-operation failure
            throw new RuntimeException("Simulated crash after deleteWeek call");
        })).hasMessageContaining("Simulated crash");

        // Verify atomicity: week and child task MUST still exist in database
        assertThat(weekRepository.existsById(weekId)).isTrue();
        assertThat(taskRepository.existsById(taskId)).isTrue();

        // Now call deleteWeek to commit cleanly
        weekService.deleteWeek(weekId);

        // Verify both are completely cleaned up
        assertThat(weekRepository.existsById(weekId)).isFalse();
        assertThat(taskRepository.existsById(taskId)).isFalse();
    }

    @Test
    @DisplayName("FILE-05: Mentorship cover image replace rollback preserves old file on disk")
    void file05_coverImageReplaceRollbackPreservesOldFile() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "file05-m1-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        authenticateAsMentor(mentorEmail);

        // Step 1: Upload initial cover image
        MockMultipartFile file1 = new MockMultipartFile("image", "cover1.png", "image/png", samplePngBytes());
        String url1 = mentorShipService.uploadCoverImage(chain.mentorship().getId(), file1);

        String fileName1 = Paths.get(url1).getFileName().toString();
        Path localPath1 = Path.of(baseDir).resolve("mentorship-cover-images").resolve(fileName1);
        cleanupPaths.add(localPath1);
        assertThat(Files.exists(localPath1)).isTrue();

        // Step 2: Attempt replace with file2 inside a rolling-back transaction
        MockMultipartFile file2 = new MockMultipartFile("image", "cover2.png", "image/png", samplePngBytes());
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.execute(status -> {
            mentorShipService.uploadCoverImage(chain.mentorship().getId(), file2);
            throw new RuntimeException("Simulated rollback during image update");
        })).hasMessageContaining("Simulated rollback");

        // Step 3: Verify DB entity still points to cover1
        MentorShip refreshed = mentorShipRepository.findById(chain.mentorship().getId()).orElseThrow();
        assertThat(refreshed.getCoverImageUrl()).isEqualTo(url1);

        // CRITICAL: File 1 MUST NOT have been deleted from disk!
        assertThat(Files.exists(localPath1)).isTrue();
    }

    @Test
    @DisplayName("FILE-05: Mentorship cover image replace commits and cleans up old file after commit")
    void file05_coverImageReplaceCommitDeletesOldFile() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "file05-m2-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        authenticateAsMentor(mentorEmail);

        // Initial image
        MockMultipartFile file1 = new MockMultipartFile("image", "initial.png", "image/png", samplePngBytes());
        String url1 = mentorShipService.uploadCoverImage(chain.mentorship().getId(), file1);
        String fileName1 = Paths.get(url1).getFileName().toString();
        Path localPath1 = Path.of(baseDir).resolve("mentorship-cover-images").resolve(fileName1);
        cleanupPaths.add(localPath1);
        assertThat(Files.exists(localPath1)).isTrue();

        // Replace with new image in committed flow
        MockMultipartFile file2 = new MockMultipartFile("image", "replacement.png", "image/png", samplePngBytes());
        String url2 = mentorShipService.uploadCoverImage(chain.mentorship().getId(), file2);
        String fileName2 = Paths.get(url2).getFileName().toString();
        Path localPath2 = Path.of(baseDir).resolve("mentorship-cover-images").resolve(fileName2);
        cleanupPaths.add(localPath2);

        // Verify DB updated to url2
        MentorShip refreshed = mentorShipRepository.findById(chain.mentorship().getId()).orElseThrow();
        assertThat(refreshed.getCoverImageUrl()).isEqualTo(url2);

        // Verify new file exists and old file has been deleted
        assertThat(Files.exists(localPath2)).isTrue();
        assertThat(Files.exists(localPath1)).isFalse();
    }

    @Test
    @DisplayName("FILE-05: Task update with invalid validation aborts and keeps existing attachment on disk")
    void file05_taskAttachmentReplaceValidationFailurePreservesOldFile() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "file05-m3-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        authenticateAsMentor(mentorEmail);

        CreateWeekrequest createReq = new CreateWeekrequest();
        createReq.setMentorshipId(chain.mentorship().getId());
        createReq.setTitle("Task Attachment Week " + suffix);
        WeekResponse weekResp = weekService.createWeek(createReq);
        Week week = weekRepository.findById(weekResp.getId()).orElseThrow();

        // Upload initial task attachment
        MockMultipartFile initialFile = new MockMultipartFile("file", "initial-task.pdf", "application/pdf", VALID_PDF);
        Task initialTask = taskRepository.saveAndFlush(Task.builder()
                .title("Original Task")
                .description("Initial description")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(45)
                .status(TaskStatus.DRAFT)
                .dueAt(java.time.LocalDateTime.now().plusDays(7))
                .week(week)
                .build());
        Long taskId = initialTask.getId();

        // Update task with first attachment
        PatchTaskRequest initialPatch = new PatchTaskRequest();
        initialPatch.setTitle("Original Task with File");
        var resp1 = taskService.updateTask(taskId, initialPatch, initialFile, mentorEmail);
        String path1 = resp1.getUploadedAttachmentPath();
        assertThat(path1).isNotBlank();

        Path localPath1 = Path.of(path1);
        cleanupPaths.add(localPath1);
        assertThat(Files.exists(localPath1)).isTrue();

        // Now attempt update with a new file, but violate business rule: passPoints (150) > points (100)
        MockMultipartFile replacementFile = new MockMultipartFile("file", "replacement-task.pdf", "application/pdf", VALID_PDF);
        PatchTaskRequest invalidPatch = new PatchTaskRequest();
        invalidPatch.setPassPoints(150); // Exceeds points (100)

        assertThatThrownBy(() -> taskService.updateTask(taskId, invalidPatch, replacementFile, mentorEmail))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Pass points must be less than or equal to points");

        // Verify task still points to path1
        Task currentTask = taskRepository.findById(taskId).orElseThrow();
        assertThat(currentTask.getUploadedAttachmentPath()).isEqualTo(path1);

        // CRITICAL: File 1 MUST NOT have been deleted from disk!
        assertThat(Files.exists(localPath1)).isTrue();
    }

    @Test
    @DisplayName("AfterCommitExecutor: standalone lifecycle handles no-tx, committed-tx, and rollback-tx")
    void afterCommitExecutor_lifecycleVerification() {
        AtomicBoolean flagNoTx = new AtomicBoolean(false);
        afterCommitExecutor.execute(() -> flagNoTx.set(true));
        assertThat(flagNoTx.get()).isTrue();

        // In committing transaction: runs in afterCommit
        AtomicBoolean flagCommitTx = new AtomicBoolean(false);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            afterCommitExecutor.execute(() -> flagCommitTx.set(true));
            // Not executed yet inside transaction
            assertThat(flagCommitTx.get()).isFalse();
            return null;
        });
        // Executed after commit
        assertThat(flagCommitTx.get()).isTrue();

        // In rolling back transaction: never runs
        AtomicBoolean flagRollbackTx = new AtomicBoolean(false);
        try {
            tx.execute(status -> {
                afterCommitExecutor.execute(() -> flagRollbackTx.set(true));
                throw new RuntimeException("Intended rollback");
            });
        } catch (RuntimeException ignored) {}
        // Must NOT run after rollback
        assertThat(flagRollbackTx.get()).isFalse();
    }
}
