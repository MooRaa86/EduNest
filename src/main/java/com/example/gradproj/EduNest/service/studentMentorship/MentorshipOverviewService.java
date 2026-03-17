package com.example.gradproj.EduNest.service.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipReviewProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MentorshipOverviewService {
    private final ReviewsRepository reviewsRepository;


    public PageResponse<MentorshipReviewProjection> getMentorshipReviews(
            Long mentorshipId, 
            String studentEmail,
            int page,
            int size
    ) {
        Page<MentorshipReviewProjection> reviewsPage = reviewsRepository.findMentorshipReviews(
                mentorshipId, 
                studentEmail, 
                PageRequest.of(page, size)
        );

        return PageResponse.<MentorshipReviewProjection>builder()
                .content(reviewsPage.getContent())
                .page(reviewsPage.getNumber())
                .size(reviewsPage.getSize())
                .totalElements(reviewsPage.getTotalElements())
                .totalPages(reviewsPage.getTotalPages())
                .build();
    }
}
