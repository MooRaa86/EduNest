package com.example.gradproj.EduNest.service.fileSotageService;

import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionalFileReplacementTest {
    @TempDir Path root;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private ImageStorageService images;

    @BeforeEach
    void setUp() {
        var ds = createDataSource();
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table profile (id int primary key, image varchar(255))");
        jdbc.update("insert into profile values (1, ?)", "/uploads/student/old.png");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var validator = mock(UploadContentValidator.class);
        when(validator.validateImageUpload(any(), anySet(), anyLong())).thenReturn("png");
        images = new ImageStorageService(root.toString(), validator, mock(SupabaseStorageService.class), new StandardEnvironment());
    }

    DataSource createDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void rollbackPreservesOldReferenceAndFileAndCleansNewUpload() throws Exception {
        Path old = root.resolve("student/old.png");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "old");
        var newUrl = new AtomicReference<String>();
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            newUrl.set(images.saveImage("student", 1L, new MockMultipartFile("image", "new.png", "image/png", new byte[]{1})));
            images.deleteImage("student", "/uploads/student/old.png");
            jdbc.update("update profile set image=? where id=1", newUrl.get());
            throw new IllegalStateException("simulate failure after DB update");
        }));
        assertEquals("/uploads/student/old.png", jdbc.queryForObject("select image from profile where id=1", String.class));
        assertTrue(Files.exists(old));
        assertFalse(Files.exists(root.resolve("student").resolve(Path.of(newUrl.get()).getFileName())));
    }

    @Test
    void commitKeepsNewReferenceAndRemovesOldFileOnlyAfterCommit() throws Exception {
        Path old = root.resolve("student/old.png");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "old");
        String newUrl = transaction.execute(status -> {
            String uploaded = images.saveImage("student", 1L, new MockMultipartFile("image", "new.png", "image/png", new byte[]{1}));
            images.deleteImage("student", "/uploads/student/old.png");
            jdbc.update("update profile set image=? where id=1", uploaded);
            assertTrue(Files.exists(old));
            return uploaded;
        });
        assertEquals(newUrl, jdbc.queryForObject("select image from profile where id=1", String.class));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(root.resolve("student").resolve(Path.of(newUrl).getFileName())));
    }

    @Test
    void privateFileRollbackRestoresReferenceAndPreservesOldFile() throws Exception {
        var files = new FileStorageService(mock(UploadContentValidator.class), mock(SupabaseStorageService.class), new StandardEnvironment());
        ReflectionTestUtils.setField(files, "baseDir", root.toString());
        files.init();
        Path old = root.resolve("submissions/old.txt");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "old");
        var saved = new AtomicReference<String>();
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            saved.set(files.saveFile("submissions", "task", 1L, 2L,
                    new MockMultipartFile("file", "new.txt", "text/plain", "new".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            files.deleteFile(old.toString());
            jdbc.update("update profile set image=? where id=1", saved.get());
            throw new IllegalStateException("rollback");
        }));
        assertEquals("/uploads/student/old.png", jdbc.queryForObject("select image from profile where id=1", String.class));
        assertTrue(Files.exists(old));
        assertFalse(Files.exists(Path.of(saved.get())));
    }

    @Test
    void remoteImageDeletionWaitsForCommitAndNewObjectIsRemovedOnRollback() {
        var validator = mock(UploadContentValidator.class);
        when(validator.validateImageUpload(any(), anySet(), anyLong())).thenReturn("png");
        var storage = mock(SupabaseStorageService.class);
        when(storage.isConfigured()).thenReturn(true);
        when(storage.uploadPublic(anyString(), anyString(), any(), anyString())).thenReturn("https://example.test/new.png");
        var remoteImages = new ImageStorageService(root.toString(), validator, storage, new StandardEnvironment());
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            remoteImages.saveImage("student", 1L, new MockMultipartFile("image", "new.png", "image/png", new byte[]{1}));
            remoteImages.deleteImage("student", "https://example.test/old.png");
            verify(storage, never()).deletePublicByUrl(anyString());
            throw new IllegalStateException("rollback");
        }));
        verify(storage, never()).deletePublicByUrl("https://example.test/old.png");
        verify(storage).deletePublic(eq("student"), anyString());
        transaction.executeWithoutResult(status -> remoteImages.deleteImage("student", "https://example.test/old.png"));
        verify(storage).deletePublicByUrl("https://example.test/old.png");
    }
}
