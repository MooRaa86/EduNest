package com.example.gradproj.EduNest.service.dashboard;

import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.ReviewsRsponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MonthlyRevenueProjection;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class DashboardService {

    private final MentorShipRepository MentorShipRepository;
    private final MentorRepository mentorRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ReviewsRepository reviewsRepository;
    private final LiveSessionRepository liveSessionRepository;

    private String getCurrentMentorEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }

    @PreAuthorize("hasRole('MENTOR')")
    public Map<String, Object> getDashboardCardsDetails() {

        Mentor mentor = mentorRepository.findByEmail(getCurrentMentorEmail())
                .orElseThrow(() -> new RuntimeException("Mentor not found"));


        long studentsCount = enrollmentRepository.countStudentsByMentorId(mentor.getId());
        long mentorshipCount = MentorShipRepository.countByMentor_Id(mentor.getId());
        Double avgRating = reviewsRepository.findAverageRatingByMentorId(mentor.getId());
        Double totalrevenue = enrollmentRepository.getTotalRevenueByMentorId(mentor.getId());


        Map<String, Object> dashboardCardsDetails = new HashMap<>();
        dashboardCardsDetails.put("Total Student", studentsCount);
        dashboardCardsDetails.put("Total Revenue", totalrevenue);
        dashboardCardsDetails.put("Total Mentorships", mentorshipCount);
        dashboardCardsDetails.put("Average Rating", avgRating);

        return dashboardCardsDetails;
    }

    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<ReviewsRsponse> getReviewsInDashboard(int page , int size){

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<MentorShipReviews> reviews = reviewsRepository.
                findByMentorShip_Mentor_Email(getCurrentMentorEmail(), pageable);

        List<ReviewsRsponse> reviewsRsponses = reviews.getContent()
                .stream()
                .map(this::mapToReviewsResponse)
                .toList();

        return PageResponse.<ReviewsRsponse>builder()
                .content(reviewsRsponses)
                .page(reviews.getNumber())
                .size(reviews.getSize())
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .build();

    }

    @PreAuthorize("hasRole('MENTOR')")
    public PageResponse<DashboardSessionResponse> getUpcomingSessionsForDashboard(
            int page,
            int size
    ) {

        String email = getCurrentMentorEmail();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("scheduledAt").ascending()
        );

        Page<Session> sessions =
                liveSessionRepository
                        .findUpcomingSessionsByMentorEmail(
                                email,
                                LocalDateTime.now(),
                                pageable
                        );

        List<DashboardSessionResponse> content = sessions.getContent()
                .stream()
                .map(this::mapToSessionResponse)
                .toList();

        return PageResponse.<DashboardSessionResponse>builder()
                .content(content)
                .page(sessions.getNumber())
                .size(sessions.getSize())
                .totalElements(sessions.getTotalElements())
                .totalPages(sessions.getTotalPages())
                .build();
    }

    public record SalesChartResponse(
            String month,
            Integer year,
            Double totalRevenue
    ) {}

    public List<SalesChartResponse> getSalesChartData(Integer months) {

        String email = getCurrentMentorEmail();

        LocalDateTime startDate = null;

        if (months != null && months > 0) {
            startDate = LocalDateTime.now().minusMonths(months);
        }

        List<MonthlyRevenueProjection> data =
                enrollmentRepository.getMonthlyRevenueForMentor(email, startDate);

        return data.stream()
                .map(p -> new SalesChartResponse(
                        Month.of(p.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        p.getYear(),
                        p.getTotalRevenue()
                ))
                .toList();
    }


    private ReviewsRsponse mapToReviewsResponse(MentorShipReviews mentorShipReviews) {
        return ReviewsRsponse.builder()
                .feedBack(mentorShipReviews.getFeedBack())
                .rating(mentorShipReviews.getRating())
                .mentorShip(mentorShipReviews.getMentorShip().getTitle())
                .studentName(mentorShipReviews.getStudent().getFirstName() + " " + mentorShipReviews.getStudent().getLastName())
                .reviewDate(mentorShipReviews.getCreatedAt())
                .build();
    }

    private DashboardSessionResponse mapToSessionResponse(Session session) {
        return DashboardSessionResponse.builder()
                .title(session.getTitle())
                .sessionStartDate(session.getScheduledAt())
                .mentorshipTitle(session.getWeek().getMentorship().getTitle())
                .weekTitle(session.getWeek().getTitle())
                .build();
    }



}
