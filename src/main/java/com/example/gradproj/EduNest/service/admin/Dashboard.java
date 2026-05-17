package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.dashboard.AdminDashboard.AdminDashboardCards;
import com.example.gradproj.EduNest.dto.dashboard.AdminDashboard.AdminFullDashDto;
import com.example.gradproj.EduNest.dto.dashboard.SessionsChartResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.projections.MonthlySessionsProjection;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.AdminDashboardCardsProjection;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.projection.TopMentorProjection;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class Dashboard {

    private final MentorShipRepository mentorShipRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final NotificationService notificationService;
    private final MentorRepository mentorRepository;

    public AdminDashboardCards getAdminDashboardCards() {
        AdminDashboardCardsProjection projection = mentorShipRepository.getAdminDashboardCards();
        return AdminDashboardCards.builder()
                .totalStudents(projection.getTotalStudents())
                .totalMentors(projection.getTotalMentors())
                .activeMentorships(projection.getActiveMentorships())
                .completedMentorships(projection.getCompletedMentorships())
                .totalRevenue(projection.getTotalRevenue())
                .build();
    }

    public List<SessionsChartResponse> getMonthlySessionsForLastMonths(Integer months) {

        LocalDateTime startDate = null;
        LocalDateTime endDate = LocalDateTime.now();

        if (months != null && months > 0) {

            startDate = LocalDate.now()
                    .withDayOfMonth(1)
                    .minusMonths(months - 1)
                    .atStartOfDay();
        }

        List<MonthlySessionsProjection> data =
                liveSessionRepository.getMonthlySessionsForLastPeriod(
                        startDate,
                        endDate
                );

        return data.stream()
                .map(p -> new SessionsChartResponse(
                        Month.of(p.getMonth())
                                .getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        p.getYear(),
                        p.getTotalSessions()
                ))
                .toList();
    }

    public PageResponse<AdminNotificationResponse> getAdminNotifications(int size, int page) {
        return notificationService.getAdminNotifications(size, page);
    }

    public PageResponse<TopMentorProjection> getTopMentorsByTotalStudents(int size, int page) {
        Pageable pageable = PageRequest.of(page, size);

        Page<TopMentorProjection> mentors = mentorRepository.
                findTopMentorsByTotalStudents(pageable);

        var response = mentors.getContent().stream().toList();

        return PageResponse.<TopMentorProjection>builder()
                .content(response)
                .page(mentors.getNumber())
                .size(mentors.getSize())
                .totalPages(mentors.getTotalPages())
                .totalElements(mentors.getTotalElements())
                .build();

    }

    public AdminFullDashDto getFullDashboard(Integer months, int notificationSize, int notificationPage,
                                                int mentorSize, int mentorPage){
        return AdminFullDashDto.builder()
                .cards(getAdminDashboardCards())
                .sessionsChart(getMonthlySessionsForLastMonths(months))
                .notifications(getAdminNotifications(notificationSize, notificationPage))
                .topMentors(getTopMentorsByTotalStudents(mentorSize, mentorPage))
                .build();
    }
}
