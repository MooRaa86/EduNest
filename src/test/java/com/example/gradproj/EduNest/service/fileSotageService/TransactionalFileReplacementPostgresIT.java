package com.example.gradproj.EduNest.service.fileSotageService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.service.week.WeekService;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import org.springframework.core.env.StandardEnvironment;

class TransactionalFileReplacementPostgresIT extends TransactionalFileReplacementTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Override
    DataSource createDataSource() {
        // Reset the small fixture table between inherited tests.
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("drop table if exists profile");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void failedWeekDeletionRollsBackDatabaseAndKeepsAttachments() throws Exception {
        DataSource ds = createDataSource();
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table if not exists eb45_test_week (id int primary key)");
        jdbc.update("insert into eb45_test_week values (1)");
        var oldFile = root.resolve("task-attachment/old.txt");
        Files.createDirectories(oldFile.getParent());
        Files.writeString(oldFile, "old");
        var files = new FileStorageService(mock(UploadContentValidator.class), mock(SupabaseStorageService.class), new StandardEnvironment());
        ReflectionTestUtils.setField(files, "baseDir", root.toString());
        files.init();
        Week week = mock(Week.class);
        MentorShip mentorship = mock(MentorShip.class);
        Mentor mentor = mock(Mentor.class);
        Task task = mock(Task.class);
        when(week.getMentorship()).thenReturn(mentorship);
        when(mentorship.getMentor()).thenReturn(mentor);
        when(mentor.getId()).thenReturn(1L);
        when(week.getTasks()).thenReturn(List.of(task));
        when(week.getProjects()).thenReturn(List.of());
        when(task.getUploadedAttachmentPath()).thenReturn(oldFile.toString());
        when(task.getSubmissions()).thenReturn(List.of());
        WeekRepository weeks = mock(WeekRepository.class);
        when(weeks.findById(1L)).thenReturn(Optional.of(week));
        doAnswer(invocation -> { jdbc.update("delete from eb45_test_week where id=1"); throw new IllegalStateException("failure after delete"); }).when(weeks).delete(week);
        MentorRepository mentors = mock(MentorRepository.class);
        when(mentors.findByEmail("local-test")).thenReturn(Optional.of(mentor));
        var service = new WeekService(mock(MentorShipRepository.class), weeks, mock(TaskRepository.class), mock(QuizRepository.class),
                mock(ProjectRepository.class), mock(LiveSessionRepository.class), mock(LectureRepository.class), mentors, files);
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("local-test", "unused", List.of()));
        try {
            assertThrows(IllegalStateException.class, () -> ((WeekService) proxy.getProxy()).deleteWeek(1L));
            assertEquals(1, jdbc.queryForObject("select count(*) from eb45_test_week", Integer.class));
            assertTrue(Files.exists(oldFile));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
