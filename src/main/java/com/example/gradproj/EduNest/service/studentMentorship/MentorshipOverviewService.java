package com.example.gradproj.EduNest.service.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.studentMentorship.AfterEnrollData;
import com.example.gradproj.EduNest.dto.studentMentorship.BeforeEnrollData;
import com.example.gradproj.EduNest.dto.studentMentorship.MentorshipDetailsDto;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipDetailsProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipReviewProjection;
import com.example.gradproj.EduNest.service.homepage.HomePageService;
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
    private final EnrollmentRepository enrollmentRepository;
    private final HomePageService homePageService;

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

    public Double getMentorshipAverageRating(Long mentorshipId) {
        return reviewsRepository.calculateAverageRating(mentorshipId);
    }

    public List<MentorshipExploreDto> getTopMentorshipsByMentorEmail(String mentorEmail, String studentEmail, int limit) {
        return mentorShipRepository.findTopByMentorEmailOrderByRating(mentorEmail, studentEmail, PageRequest.of(0, limit));
    }

    public MentorshipDetailsDto getMentorshipWithEnrollmentStatus(
            Long mentorshipId, String studentEmail, int page, int size, int topMentorshipLimit) {

        boolean isEnrolled = false;

        if (studentEmail != null) {
            isEnrolled = enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, studentEmail);
        }
        
        MentorshipDetailsProjection mentorship = mentorShipRepository.findMentorshipDetailsById(mentorshipId);
        
        if (mentorship == null) {
            throw new UsernameNotFoundException("Mentorship not found");
        }
        
        List<String> tags = mentorShipRepository.findTagsByMentorshipId(mentorshipId);
        List<String> whatWillLearn = mentorShipRepository.findWhatWillLearnByMentorshipId(mentorshipId);
        
        if (!isEnrolled) {
            Double finalPrice = mentorship.getPrice() * (1.0 - mentorship.getDiscountPercentage() / 100.0);
            List<MentorshipExploreDto> topMentorships = mentorShipRepository.findTopByMentorEmailOrderByRating(
                    mentorship.getMentorEmail(), studentEmail, PageRequest.of(0, topMentorshipLimit));
            
            BeforeEnrollData beforeEnroll = BeforeEnrollData.builder()
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
                    .mentorProfileImageUrl(mentorship.getMentorProfileImageUrl())
                    .mentorJobTitle(mentorship.getMentorJobTitle())
                    .mentorYearsOfExperience(mentorship.getMentorYearsOfExperience())
                    .tags(tags)
                    .whatWillLearn(whatWillLearn)
                    .topMentorMentorships(topMentorships)
                    .build();
            
            return MentorshipDetailsDto.builder()
                    .beforeEnroll(beforeEnroll)
                    .afterEnroll(null)
                    .build();
        } else {
            AfterEnrollData afterEnroll = AfterEnrollData.builder()
                    .id(mentorship.getId())
                    .title(mentorship.getTitle())
                    .subtitle(mentorship.getSubtitle())
                    .description(mentorship.getDescription())
                    .category(mentorship.getCategory())
                    .coverImageUrl(mentorship.getCoverImageUrl())
                    .whatWillLearn(whatWillLearn)
                    .tags(tags)
                    .mentorName(mentorship.getMentorName())
                    .mentorEmail(mentorship.getMentorEmail())
                    .mentorProfileImageUrl(mentorship.getMentorProfileImageUrl())
                    .mentorJobTitle(mentorship.getMentorJobTitle())
                    .mentorYearsOfExperience(mentorship.getMentorYearsOfExperience())
                    .progress(homePageService.getStudentMentorshipProgress(studentEmail, mentorshipId))
                    .upcomingItems(homePageService.getUpcomingItemsByMentorship(studentEmail, mentorshipId, page, size))
                    .build();
            
            return MentorshipDetailsDto.builder()
                    .beforeEnroll(null)
                    .afterEnroll(afterEnroll)
                    .build();
        }
    }

}
