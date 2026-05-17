package com.example.gradproj.EduNest.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private final TaskDeadlineReminderService taskDeadlineReminderService;
    private final ProjectDeadlineReminderService projectDeadlineReminderService;

    @Scheduled(cron = "0 0 * * * *") // every hour
    public void sendDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next24Hours = now.plusHours(24);

        log.info("Starting deadline reminder checks...");

        try {
            taskDeadlineReminderService.remindUpcomingTasks(now, next24Hours);
        } catch (Exception e) {
            log.error("Error during task reminders: {}", e.getMessage(), e);
        }

        try {
            projectDeadlineReminderService.remindUpcomingProjects(now, next24Hours);
        } catch (Exception e) {
            log.error("Error during project reminders: {}", e.getMessage(), e);
        }

        log.info("Completed deadline reminder checks.");
    }
}