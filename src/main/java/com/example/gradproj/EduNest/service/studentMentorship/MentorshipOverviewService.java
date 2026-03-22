package com.example.gradproj.EduNest.service.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.studentMentorship.MentorshipOverviewDto;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipOverviewProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipReviewProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorshipOverviewService {
    private final ReviewsRepository reviewsRepository;
    private final MentorShipRepository mentorShipRepository;

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

    public MentorshipOverviewDto getMentorshipWithEnrollmentStatus(
            Long mentorshipId, String studentEmail) {
        MentorshipOverviewProjection mentorship = mentorShipRepository.findMentorshipOverview(mentorshipId, studentEmail);
        
        if (mentorship == null) {
            throw new UsernameNotFoundException("Mentorship not found");
        }

        List<String> tags = mentorShipRepository.findTagsByMentorshipId(mentorshipId);
        List<String> whatWillLearn = mentorShipRepository.findWhatWillLearnByMentorshipId(mentorshipId);

        Double finalPrice = mentorship.getPrice() * (1.0 - mentorship.getDiscountPercentage() / 100.0);

        return MentorshipOverviewDto.builder()
                .id(mentorship.getId())
                .title(mentorship.getTitle())
                .subtitle(mentorship.getSubtitle())
                .description(mentorship.getDescription())
                .category(mentorship.getCategory())
                .difficultyLevel(mentorship.getDifficultyLevel())
                .duration(mentorship.getDuration())
                .price(mentorship.getPrice())
                .discountPercentage(mentorship.getDiscountPercentage())
                .finalPrice(finalPrice)
                .coverImageUrl(mentorship.getCoverImageUrl())
                .status(mentorship.getStatus())
                .rating(mentorship.getRating())
                .mentorName(mentorship.getMentorName())
                .mentorEmail(mentorship.getMentorEmail())
                .mentorCoverImageUrl(mentorship.getMentorProfileImageUrl())
                .mentorYearsOfExperience(mentorship.getMentorYearsOfExperience())
                .isEnrolled(mentorship.getIsEnrolled())
                .tags(tags)
                .whatWillLearn(whatWillLearn)
                .build();
    }

    public List<MentorshipExploreDto> getTopMentorshipsByMentorEmail(String mentorEmail, int limit) {
        return mentorShipRepository.findTopByMentorEmailOrderByRating(mentorEmail, PageRequest.of(0, limit));
    }
}
