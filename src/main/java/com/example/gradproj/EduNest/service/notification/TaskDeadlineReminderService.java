package com.example.gradproj.EduNest.service.notification;

import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDeadlineReminderService {

    private final TaskRepository taskRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final NotificationService notificationService;

    @Async
    @Transactional
    public void remindUpcomingTasks(LocalDateTime from, LocalDateTime to) {
        List<Task> upcomingTasks = taskRepository.findTasksWithUpcomingDeadline(
                TaskStatus.PUBLISHED,
                from,
                to
        );

        log.info("Found {} tasks with upcoming deadlines", upcomingTasks.size());

        for (Task task : upcomingTasks) {
            sendTaskReminders(task);
        }
    }


    private void sendTaskReminders(Task task) {
        Long mentorshipId = task.getWeek().getMentorship().getId();
        String mentorshipTitle = task.getWeek().getMentorship().getTitle();

        List<Student> students = enrollmentRepository.findStudentsByMentorshipId(mentorshipId);

        Set<Long> submittedStudentIds = taskSubmissionRepository
                .findStudentIdsByTaskId(task.getId())
                .stream()
                .collect(Collectors.toSet());

        int remindersSent = 0;

        for (Student student : students) {
            if (!submittedStudentIds.contains(student.getId())) {
                try {
                    notificationService.sendToUserByEmail(
                            student.getEmail(),
                            "Task Deadline Reminder",
                            "Reminder: Task \"" + task.getTitle()
                                    + "\" in mentorship \"" + mentorshipTitle
                                    + "\" is due within 24 hours!",
                            NotificationType.TASK
                    );

                    remindersSent++;

                } catch (Exception e) {
                    log.warn("Failed to send task reminder to student {} for task {}: {}",
                            student.getEmail(),
                            task.getId(),
                            e.getMessage()
                    );
                }
            }
        }

        log.info("Sent {} task deadline reminders for '{}' (id={})",
                remindersSent,
                task.getTitle(),
                task.getId()
        );
    }
}