package com.example.gradproj.EduNest.service.profile;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.profile.StudentProjectProfileDTO;
import com.example.gradproj.EduNest.dto.profile.request.UpdateStudentProfileRequest;
import com.example.gradproj.EduNest.dto.profile.response.StudentProfileInformationResponse;
import com.example.gradproj.EduNest.dto.skill.response.SkillResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.BadgeAchievementResponse;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.socialMedia.Media;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.certificate.CertificateService;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import com.example.gradproj.EduNest.service.skill.StudentSkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentProfileService {
    private final StudentRepository studentRepository;
    private final ImageStorageService imageStorageService;
    private final BadgeAwardRepository badgeAwardRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final StudentSkillService studentSkillService;
    private final CertificateService certificateService;
    private static final String STUDENT_IMAGE_FOLDER = "student";


    public StudentProfileInformationResponse getStudentProfile() {
        Student student = getCurrentStudent();

        List<BadgeAchievementResponse> badges = badgeAwardRepository
                .findByStudent_IdOrderByCreatedAtDesc(student.getId())
                .stream()
                .map(a -> BadgeAchievementResponse.builder()
                        .id(a.getBadge().getId())
                        .title(a.getBadge().getTitle())
                        .description(a.getBadge().getDescription())
                        .points(a.getBadge().getPoints())
                        .mentorshipId(a.getBadge().getMentorship().getId())
                        .mentorshipTitle(a.getBadge().getMentorship().getTitle())
                        .awardedByFullName(a.getAwardedBy().getFirstName() + " " + a.getAwardedBy().getLastName())
                        .awardedAt(a.getCreatedAt())
                        .build())
                .toList();

        List<StudentProjectProfileDTO> projects =
                projectSubmissionRepository
                        .findGradedForStudentProfile(student.getId(), PageRequest.of(0, 10))
                        .getContent()
                        .stream()
                        .map(ps -> StudentProjectProfileDTO.builder()
                                .projectSubmissionId(ps.getId())
                                .projectTitle(ps.getProject().getTitle())
                                .mentorshipTitle(ps.getProject().getWeek().getMentorship().getTitle())
                                .status(ps.getStatus())
                                .submittedAt(ps.getSubmittedAt())
                                .gradedAt(ps.getGradedAt())
                                .submissionLink(ps.getFileUrl())
                                .feedback(ps.getFeedBack())
                                .rawScore(ps.getRawScore())
                                .finalScore(ps.getFinalScore())
                                .build())
                        .toList();

        List<String>skills=studentSkillService.getAllStudentSkills().stream()
                .map(SkillResponse::getSkillName)
                .toList();

        PageResponse<CertificateResponse> certificates = certificateService
                .getStudentCertificates(student.getEmail(), 0, 10);

        return StudentProfileInformationResponse.builder()
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .fullName(student.getFirstName() + " " + student.getLastName())
                .email(student.getEmail())
                .jobTitle(student.getJobTitle())
                .bio(student.getBio())
                .profileImageUrl(student.getProfileImageUrl())
                .githubLink(getSocialLink(student, Media.GITHUB))
                .linkedInLink(getSocialLink(student, Media.LINKEDIN))
                .badges(badges)
                .projects(projects)
                .skills(skills)
                .certificates(certificates)
                .build();
    }

    @Transactional
    public void updateStudentProfile(UpdateStudentProfileRequest request) {
        Student student = getCurrentStudent();

        if (request.getFirstName() != null) student.setFirstName(request.getFirstName());
        if (request.getLastName() != null) student.setLastName(request.getLastName());
        if (request.getBio() != null) student.setBio(request.getBio());
        if (request.getJobTitle() != null) student.setJobTitle(request.getJobTitle());

        if (request.getSocialMediaLinks() != null) {
            request.getSocialMediaLinks().forEach(link -> {
                student.getSocialMediaLinks().stream()
                        .filter(sm -> sm.getName().equals(link.getName()))
                        .findFirst()
                        .ifPresentOrElse(
                                sm -> sm.setUrl(link.getUrl()),
                                () -> student.getSocialMediaLinks().add(SocialMedia.builder()
                                        .name(link.getName())
                                        .url(link.getUrl())
                                        .user(student)
                                        .build())
                        );
            });
        }

        studentRepository.save(student);
    }

    public String updateProfileImage(MultipartFile image) {
        Student student = getCurrentStudent();

        if (image != null && !image.isEmpty()) {
            imageStorageService.deleteImage(STUDENT_IMAGE_FOLDER, student.getProfileImageUrl());
            String newImageUrl = imageStorageService.saveImage(STUDENT_IMAGE_FOLDER, student.getId(), image);
            student.setProfileImageUrl(newImageUrl);
            studentRepository.save(student);
            return newImageUrl;
        }
        throw new globalLogicEx("Image is empty");
    }

    private String getSocialLink(Student student, Media media) {
        return student.getSocialMediaLinks().stream()
                .filter(sm -> media.equals(sm.getName()))
                .map(SocialMedia::getUrl)
                .findFirst()
                .orElse(null);
    }

    private Student getCurrentStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return studentRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
    }

}
