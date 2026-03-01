package com.example.gradproj.EduNest.service.profile;

import com.example.gradproj.EduNest.dto.profile.request.UpdateMentorProfileRequest;
import com.example.gradproj.EduNest.dto.profile.response.MentorProfileInformationResponse;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MentorProfileInfoService {
    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final ImageStorageService imageStorageService;
    private static final String MENTOR_IMAGE_FOLDER = "mentor";

    public MentorProfileInformationResponse getCurrentUserInformation() {
        UserEntity user = getCurrentUser();

        Mentor mentor = mentorRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));

        return MentorProfileInformationResponse
                .builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .email(user.getEmail())
                .bio(mentor.getBio())
                .jobTitle(mentor.getJobTitle())
                .yearsOfExperience(mentor.getYearsOfExperience())
                .githubLink(mentor.getSocialMedia() != null ? mentor.getSocialMedia().getGithub() : null)
                .linkedInLink(mentor.getSocialMedia() != null ? mentor.getSocialMedia().getLinkedin() : null)
                .profileImageUrl(mentor.getProfileImageUrl())
                .build();
    }

    public void updateProfile(UpdateMentorProfileRequest request) {
        UserEntity user = getCurrentUser();
        Mentor mentor = mentorRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new globalLogicEx("Mentor not found"));

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());


        if (request.getBio() != null) mentor.setBio(request.getBio());
        if (request.getJobTitle() != null) mentor.setJobTitle(request.getJobTitle());
        if (request.getYearsOfExperience() != null) mentor.setYearsOfExperience(request.getYearsOfExperience());

        if (request.getGithubLink() != null) {
            if (mentor.getSocialMedia() == null) mentor.setSocialMedia(new SocialMedia());
            mentor.getSocialMedia().setGithub(request.getGithubLink());
        }
        if (request.getLinkedInLink() != null) {
            if (mentor.getSocialMedia() == null) mentor.setSocialMedia(new SocialMedia());
            mentor.getSocialMedia().setLinkedin(request.getLinkedInLink());
        }


        userRepository.save(user);
        mentorRepository.save(mentor);
    }


    public String updateProfileImage(MultipartFile image) {
        UserEntity user = getCurrentUser();
        Mentor mentor = mentorRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));

        if (image != null && !image.isEmpty()) {
            imageStorageService.deleteImage(MENTOR_IMAGE_FOLDER,mentor.getProfileImageUrl());
            String newImageUrl = imageStorageService.saveImage(MENTOR_IMAGE_FOLDER,mentor.getId(), image);
            mentor.setProfileImageUrl(newImageUrl);
            mentorRepository.save(mentor);
            return newImageUrl;
        }
        throw new globalLogicEx("Image is empty");
    }

    private UserEntity getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new globalLogicEx("User not found"));
    }
}
