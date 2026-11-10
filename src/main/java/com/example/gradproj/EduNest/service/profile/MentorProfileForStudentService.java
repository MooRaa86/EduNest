package com.example.gradproj.EduNest.service.profile;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.*;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.SocialMediaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorProfileForStudentService {

    private final MentorRepository mentorRepository;
    private final MentorShipRepository mentorShipRepository;
    private final ReviewsRepository reviewsRepository;
    private final SocialMediaRepository socialMediaRepository;

    private boolean isUuid(String str) {
        if (str == null || str.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isBlank()) {
            return false;
        }
        return str.chars().allMatch(Character::isDigit);
    }

    private void requireVisibleMentor(String identifier) {
        var mentor = isUuid(identifier) ? mentorRepository.findByPublicId(UUID.fromString(identifier))
                : isNumeric(identifier) ? mentorRepository.findById(Long.parseLong(identifier))
                : mentorRepository.findByEmail(identifier);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (mentor.isEmpty() || (mentor.get().isDeleted() && !admin)) throw new EntityNotFoundException("Mentor not found");
    }

    public MentorProfileforStudentDto getMentorProfile(String identifier) {
        requireVisibleMentor(identifier);
        MentorProfileforStudentDto mentorProfile;
        List<SocialMedia> socialMedia;

        if (isUuid(identifier)) {
            UUID mentorPublicId = UUID.fromString(identifier);
            mentorProfile = mentorRepository.findMentorProfileByPublicId(mentorPublicId);
            if (mentorProfile == null) {
                throw new EntityNotFoundException("Mentor not found with UUID: " + mentorPublicId);
            }
            socialMedia = socialMediaRepository.findByUser_PublicId(mentorPublicId);
        } else if (isNumeric(identifier)) {
            Long mentorId = Long.parseLong(identifier);
            mentorProfile = mentorRepository.findMentorProfileById(mentorId);
            if (mentorProfile == null) {
                throw new EntityNotFoundException("Mentor not found with ID: " + mentorId);
            }
            socialMedia = socialMediaRepository.findByUserId(mentorId);
        } else {
            mentorProfile = mentorRepository.findMentorProfileByEmail(identifier);
            if (mentorProfile == null) {
                throw new EntityNotFoundException("Mentor not found with email: " + identifier);
            }
            socialMedia = socialMediaRepository.findByUserEmail(identifier);
        }

        List<SocialMediaLinksDto> socialMediaLinks = socialMedia.stream()
                .map(s -> SocialMediaLinksDto.builder()
                        .media(s.getName())
                        .link(s.getUrl())
                        .build())
                .toList();
        mentorProfile.setSocialMediaLinks(socialMediaLinks);
        return mentorProfile;
    }

    public PageResponse<MentorProfileMentorshipsDto> getMentorMentorships(String identifier, int size, int page) {
        requireVisibleMentor(identifier);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<MentorProfileMentorshipsDto> mentorshipsPage;

        if (isUuid(identifier)) {
            UUID mentorPublicId = UUID.fromString(identifier);
            if (!mentorRepository.existsByPublicId(mentorPublicId)) {
                throw new EntityNotFoundException("Mentor not found with UUID: " + mentorPublicId);
            }
            mentorshipsPage = mentorShipRepository.findMentorshipsByMentorPublicId(mentorPublicId, pageable);
        } else if (isNumeric(identifier)) {
            Long mentorId = Long.parseLong(identifier);
            if (!mentorRepository.existsById(mentorId)) {
                throw new EntityNotFoundException("Mentor not found with ID: " + mentorId);
            }
            mentorshipsPage = mentorShipRepository.findMentorshipsByMentorId(mentorId, pageable);
        } else {
            if (!mentorRepository.existsByEmail(identifier)) {
                throw new EntityNotFoundException("Mentor not found with email: " + identifier);
            }
            mentorshipsPage = mentorShipRepository.findMentorshipsByMentorEmail(identifier, pageable);
        }

        return PageResponse.<MentorProfileMentorshipsDto>builder()
                .content(mentorshipsPage.getContent())
                .page(mentorshipsPage.getNumber())
                .size(mentorshipsPage.getSize())
                .totalElements(mentorshipsPage.getTotalElements())
                .totalPages(mentorshipsPage.getTotalPages())
                .build();
    }

    public PageResponse<MentorProfileReviews> getMentorReviews(String identifier, int size, int page) {
        requireVisibleMentor(identifier);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<MentorProfileReviews> reviewsPage;

        if (isUuid(identifier)) {
            UUID mentorPublicId = UUID.fromString(identifier);
            if (!mentorRepository.existsByPublicId(mentorPublicId)) {
                throw new EntityNotFoundException("Mentor not found with UUID: " + mentorPublicId);
            }
            reviewsPage = reviewsRepository.findReviewsByMentorPublicId(mentorPublicId, pageable);
        } else if (isNumeric(identifier)) {
            Long mentorId = Long.parseLong(identifier);
            if (!mentorRepository.existsById(mentorId)) {
                throw new EntityNotFoundException("Mentor not found with ID: " + mentorId);
            }
            reviewsPage = reviewsRepository.findReviewsByMentorId(mentorId, pageable);
        } else {
            if (!mentorRepository.existsByEmail(identifier)) {
                throw new EntityNotFoundException("Mentor not found with email: " + identifier);
            }
            reviewsPage = reviewsRepository.findReviewsByMentorEmail(identifier, pageable);
        }

        return PageResponse.<MentorProfileReviews>builder()
                .content(reviewsPage.getContent())
                .page(reviewsPage.getNumber())
                .size(reviewsPage.getSize())
                .totalElements(reviewsPage.getTotalElements())
                .totalPages(reviewsPage.getTotalPages())
                .build();
    }

    public MentorProfileCompleteDto getMentorFullProfile(String identifier,
                                                         int msSize, int msPage,
                                                         int reviewsSize, int reviewsPage) {
        var mentorProfile = getMentorProfile(identifier);
        var mentorships = getMentorMentorships(identifier, msSize, msPage);
        var reviews = getMentorReviews(identifier, reviewsSize, reviewsPage);
        return MentorProfileCompleteDto.builder()
                .mentorProfile(mentorProfile)
                .mentorships(mentorships)
                .reviews(reviews)
                .build();
    }
}
