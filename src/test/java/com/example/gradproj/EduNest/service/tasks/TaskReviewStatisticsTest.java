package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.ContentReadGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TaskReviewStatisticsTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final MentorShipRepository mentorships = mock(MentorShipRepository.class);
    private final TaskSubmissionRepository submissions = mock(TaskSubmissionRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final TaskServiceImpl service = new TaskServiceImpl(tasks, mentorships, mock(WeekRepository.class),
            submissions, enrollments, mock(NotificationService.class), mock(FileStorageService.class),
            mock(ContentReadGuard.class));

    @Test
    void noSubmissionsMeansNoPendingReviewsEvenWithEnrolledStudents() {
        var task = Task.builder().id(1L).points(10).week(Week.builder()
                .mentorship(MentorShip.builder().id(2L).build()).build()).build();
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(mentorships.existsByIdAndMentor_Email(2L, "owner")).thenReturn(true);
        when(enrollments.countStudentsByMentorship(2L)).thenReturn(5L);
        when(submissions.findByTask_Id(eq(1L), any())).thenReturn(Page.empty());

        assertThat(service.getTaskStatistics(1L, PageRequest.of(0, 1), "owner").getPendingReview()).isZero();
        assertThat(service.getTaskStatistics(1L, PageRequest.of(0, 20), "owner").getPendingReview()).isZero();
    }

    @Test
    void reviewCountIsIndependentOfPageAndEnrollmentCount() {
        var task = Task.builder().id(1L).points(10).week(Week.builder()
                .mentorship(MentorShip.builder().id(2L).build()).build()).build();
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(mentorships.existsByIdAndMentor_Email(2L, "owner")).thenReturn(true);
        when(enrollments.countStudentsByMentorship(2L)).thenReturn(50L);
        when(submissions.findByTask_Id(eq(1L), any())).thenReturn(Page.empty());
        when(submissions.countByTask_IdAndStatus(1L, SubmissionStatus.SUBMITTED)).thenReturn(3L);

        assertThat(service.getTaskStatistics(1L, PageRequest.of(0, 1), "owner").getPendingReview()).isEqualTo(3);
        assertThat(service.getTaskStatistics(1L, PageRequest.of(2, 10), "owner").getPendingReview()).isEqualTo(3);
        verify(submissions, times(2)).countByTask_IdAndStatus(1L, SubmissionStatus.SUBMITTED);
    }

    @Test
    void foreignMentorCannotReadStatistics() {
        var task = Task.builder().id(1L).week(Week.builder()
                .mentorship(MentorShip.builder().id(2L).build()).build()).build();
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.getTaskStatistics(1L, PageRequest.of(0, 1), "other"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(submissions);
    }
}
