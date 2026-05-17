package com.example.gradproj.EduNest.service.notification;

import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
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
public class ProjectDeadlineReminderService {

    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final NotificationService notificationService;

    @Async
    @Transactional
    public void remindUpcomingProjects(LocalDateTime from, LocalDateTime to) {
        List<Project> upcomingProjects = projectRepository.findProjectsWithUpcomingDeadline(
                ProjectStatus.PUBLISHED,
                from,
                to
        );

        log.info("Found {} projects with upcoming deadlines", upcomingProjects.size());

        for (Project project : upcomingProjects) {
            sendProjectReminders(project);
        }
    }



    private void sendProjectReminders(Project project) {
        Long mentorshipId = project.getWeek().getMentorship().getId();
        String mentorshipTitle = project.getWeek().getMentorship().getTitle();

        List<Student> students = enrollmentRepository.findStudentsByMentorshipId(mentorshipId);

        Set<Long> submittedStudentIds = projectSubmissionRepository
                .findStudentIdsByProjectId(project.getId())
                .stream()
                .collect(Collectors.toSet());

        int remindersSent = 0;

        for (Student student : students) {
            if (!submittedStudentIds.contains(student.getId())) {
                try {
                    notificationService.sendToUserByEmail(
                            student.getEmail(),
                            "Project Deadline Reminder",
                            "Reminder: Project \"" + project.getTitle()
                                    + "\" in mentorship \"" + mentorshipTitle
                                    + "\" is due within 24 hours!",
                            NotificationType.PROJECT
                    );

                    remindersSent++;

                } catch (Exception e) {
                    log.warn("Failed to send project reminder to student {} for project {}: {}",
                            student.getEmail(),
                            project.getId(),
                            e.getMessage()
                    );
                }
            }
        }

        log.info("Sent {} project deadline reminders for '{}' (id={})",
                remindersSent,
                project.getTitle(),
                project.getId()
        );
    }
}