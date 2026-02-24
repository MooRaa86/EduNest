package com.example.gradproj.EduNest.service.dashboard;


import com.example.gradproj.EduNest.dto.dashboard.MentorshipDashboardResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.ReviewsRsponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorShipListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipStatsResponse;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.points.projection.TopStudentResponse;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorshipDashboardService {

    private final ReviewsRepository reviewsRepository;
    private final MentorShipRepository mentorShipRepository;
    private final MentorRepository mentorRepository;
    private final TotalPointsRepository totalPointsRepository;

    private String getCurrentUserEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }

    public PageResponse<MentorShipListResponse> getMentorMentorships(
            int page,
            int size
    ) {

        String email = getCurrentUserEmail();

        if (!(mentorRepository.existsByEmail(email))) {
            throw new UsernameNotFoundException("Mentor not found");
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<MentorShipListResponse> result =
                mentorShipRepository.findMentorMentorships(email, pageable);

        return PageResponse.<MentorShipListResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public MentorshipStatsResponse getStats(Long mentorshipId) {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        MentorshipStatsResponse stats =
                mentorShipRepository.getMentorshipStats(mentorshipId, email);

        if (stats == null) {
            throw new UsernameNotFoundException("Mentorship not found or not authorized");
        }

        return stats;
    }

    public PageResponse<ReviewsRsponse> getReviewsForMentorship(
            int page , int size ,long id
    ){

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<MentorShipReviews> reviews = reviewsRepository.
                findByMentorShip_Id(id, pageable);

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

    public PageResponse<TopStudentResponse> findTopLearnersByMentorshipId(
        long mentorshipId , int page, int size
    ){
        Pageable pageable = PageRequest.of(page , size, Sort.by("createdAt").descending() );
        Page<TopStudentResponse> Page = totalPointsRepository
                .findTopStudentsByMentorship(mentorshipId, pageable);
        return PageResponse.<TopStudentResponse>builder()
                .content(Page.getContent())
                .page(Page.getNumber())
                .size(Page.getSize())
                .totalPages(Page.getTotalPages())
                .totalElements(Page.getTotalElements())
                .build();
    }

    public MentorshipDashboardResponse getFullMentorshipDashboard(

            Long mentorshipId,

            int reviewsPage,
            int reviewsSize,

            int topLearnersPage,
            int topLearnersSize
    ) {

        MentorshipStatsResponse stats =
                getStats(mentorshipId);

        PageResponse<ReviewsRsponse> reviews =
                getReviewsForMentorship(
                        reviewsPage,
                        reviewsSize,
                        mentorshipId
                );

        PageResponse<TopStudentResponse> topLearners =
                findTopLearnersByMentorshipId(
                        mentorshipId,
                        topLearnersPage,
                        topLearnersSize
                );

        return MentorshipDashboardResponse.builder()
                .stats(stats)
                .reviews(reviews)
                .topLearners(topLearners)
                .build();
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

}