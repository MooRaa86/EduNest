package com.example.gradproj.EduNest.service.profile;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.*;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.SocialMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorProfileForStudentService {

    private final MentorRepository mentorRepository;
    private final MentorShipRepository mentorShipRepository;
    private final ReviewsRepository reviewsRepository;
    private final SocialMediaRepository socialMediaRepository;

    public MentorProfileforStudentDto getMentorProfile(String mentorEmail) {
        MentorProfileforStudentDto mentorProfile = mentorRepository.findMentorProfileByEmail(mentorEmail);
        List<SocialMedia> socialMedia = socialMediaRepository.findByUserEmail(mentorEmail);
        List<SocialMediaLinksDto> socialMediaLinks = socialMedia.stream()
                .map(s -> SocialMediaLinksDto.builder()
                        .media(s.getName())
                        .link(s.getUrl())
                        .build())
                .toList();
        mentorProfile.setSocialMediaLinks(socialMediaLinks);
        return mentorProfile;
    }

    public PageResponse<MentorProfileMentorshipsDto> getMentorMentorships(String mentorEmail, int size,int Page) {
        Pageable pageable = Pageable.ofSize(size).withPage(Page);

        Page<MentorProfileMentorshipsDto> mentorshipsPage =
                mentorShipRepository.findMentorshipsByMentorEmail(mentorEmail, pageable);

        return PageResponse.<MentorProfileMentorshipsDto>builder()
                .content(mentorshipsPage.getContent())
                .page(mentorshipsPage.getNumber())
                .size(mentorshipsPage.getSize())
                .totalElements(mentorshipsPage.getTotalElements())
                .totalPages(mentorshipsPage.getTotalPages())
                .build();
    }

    public PageResponse<MentorProfileReviews> getMentorReviews(String mentorEmail, int size,int Page) {
        Pageable pageable = Pageable.ofSize(size).withPage(Page);

        Page<MentorProfileReviews> reviewsPage =
                reviewsRepository.findReviewsByMentorEmail(mentorEmail, pageable);

        return PageResponse.<MentorProfileReviews>builder()
                .content(reviewsPage.getContent())
                .page(reviewsPage.getNumber())
                .size(reviewsPage.getSize())
                .totalElements(reviewsPage.getTotalElements())
                .totalPages(reviewsPage.getTotalPages())
                .build();
    }

    public MentorProfileCompleteDto getMentorFullProfile(String email,
                                                         int msSize,int msPage,
                                                         int reviewsSize,int reviewsPage
    ){
        var mentorProfile = getMentorProfile(email);
        var mentorships = getMentorMentorships(email, msSize,msPage);
        var reviews = getMentorReviews(email, reviewsSize, reviewsPage);
        return MentorProfileCompleteDto.builder()
                .mentorProfile(mentorProfile)
                .mentorships(mentorships)
                .reviews(reviews)
                .build();
    }
}
