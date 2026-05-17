package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.admin.request.CreateAdminBadgeRequest;
import com.example.gradproj.EduNest.dto.admin.request.UpdateAdminBadgeRequest;
import com.example.gradproj.EduNest.dto.admin.response.AdminBadgeResponse;
import com.example.gradproj.EduNest.dto.admin.response.UserAdminBadgeResponse;
import com.example.gradproj.EduNest.entity.admin.AdminBadge;
import com.example.gradproj.EduNest.entity.admin.UserAdminBadge;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.admin.AdminBadgeRepository;
import com.example.gradproj.EduNest.repository.admin.UserAdminBadgeRepository;
import com.example.gradproj.EduNest.repository.admin.projection.UserAdminBadgeDetailProjection;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminBadgeService {

    private final AdminBadgeRepository adminBadgeRepository;
    private final UserAdminBadgeRepository userAdminBadgeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final BadgePdfGeneratorService badgePdfGeneratorService;

    @PreAuthorize("hasRole('ADMIN')")
    public UserAdminBadgeResponse awardBadgeToUser(Long userId, Long badgeId, String recognitionNote) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new globalLogicEx("User not found"));

        AdminBadge badge = adminBadgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Admin badge not found"));

        if (userAdminBadgeRepository.existsByUserIdAndAdminBadgeId(userId, badgeId)) {
            throw new globalLogicEx("User already has this badge assigned");
        }

        UserAdminBadge userAdminBadge = UserAdminBadge.builder()
                .user(user)
                .adminBadge(badge)
                .recognitionNote(recognitionNote)
                .awardedAt(LocalDateTime.now())
                .build();

        UserAdminBadge saved = userAdminBadgeRepository.save(userAdminBadge);

        // Send email only after transaction commits successfully
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendBadgeAwardEmail(user, badge, recognitionNote);
            }
        });
        notificationService.sendToUserByEmail(
                user.getEmail(),
                "Badge Awarded! ",
                "Congratulations! You earned the badge \"" + badge.getName()+"\""+" from admin " ,
                NotificationType.BADGE
        );

        return toUserAdminBadgeDto(saved);
    }

    @Transactional(readOnly = true)
    public List<UserAdminBadgeResponse> getUserBadges(Long userId) {
        return userAdminBadgeRepository.findUserAdminBadgesByIdOptimized(userId)
                .stream()
                .map(this::toUserAdminBadgeDtoFromProjection)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void removeBadgeFromUser(Long userBadgeId) {
        if (!userAdminBadgeRepository.existsById(userBadgeId)) {
            throw new globalLogicEx("User admin badge not found");
        }
        userAdminBadgeRepository.deleteById(userBadgeId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AdminBadgeResponse createAdminBadge(CreateAdminBadgeRequest req) {
        AdminBadge badge = AdminBadge.builder()
                .name(req.getName())
                .description(req.getDescription())
                .type(req.getType())
                .build();
        return toAdminBadgeDto(adminBadgeRepository.save(badge));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AdminBadgeResponse updateAdminBadge(Long badgeId, UpdateAdminBadgeRequest req) {
        AdminBadge badge = adminBadgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Admin badge not found"));
        if (req.getName() != null) badge.setName(req.getName());
        if (req.getDescription() != null) badge.setDescription(req.getDescription());
        if (req.getType() != null) badge.setType(req.getType());
        return toAdminBadgeDto(adminBadgeRepository.save(badge));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAdminBadge(Long badgeId) {
        if (!adminBadgeRepository.existsById(badgeId)) {
            throw new globalLogicEx("Admin badge not found");
        }
        if (userAdminBadgeRepository.existsByAdminBadgeId(badgeId)) {
            throw new globalLogicEx("Badge has already been assigned and cannot be deleted");
        }
        adminBadgeRepository.deleteById(badgeId);
    }

    @Transactional(readOnly = true)
    public List<AdminBadgeResponse> getAllAvailableBadges() {
        return adminBadgeRepository.findAll()
                .stream()
                .map(this::toAdminBadgeDto)
                .toList();
    }

    private AdminBadgeResponse toAdminBadgeDto(AdminBadge badge) {
        return AdminBadgeResponse.builder()
                .id(badge.getId())
                .name(badge.getName())
                .description(badge.getDescription())
                .type(badge.getType())
                .build();
    }

    private UserAdminBadgeResponse toUserAdminBadgeDto(UserAdminBadge uab) {
        return UserAdminBadgeResponse.builder()
                .id(uab.getId())
                .userId(uab.getUser().getId())
                .userFullName(uab.getUser().getFirstName() + " " + uab.getUser().getLastName())
                .badgeId(uab.getAdminBadge().getId())
                .badgeName(uab.getAdminBadge().getName())
                .badgeDescription(uab.getAdminBadge().getDescription())
                .badgeType(uab.getAdminBadge().getType())
                .recognitionNote(uab.getRecognitionNote())
                .awardedAt(uab.getAwardedAt())
                .build();
    }

    private void sendBadgeAwardEmail(UserEntity user, AdminBadge badge, String recognitionNote) {
        String template = emailService.getEmailTemplate("badge-award.html");

        // Sanitize all user-controlled values before injecting into HTML
        String safeName        = HtmlUtils.htmlEscape(user.getFirstName() + " " + user.getLastName());
        String safeBadgeName   = HtmlUtils.htmlEscape(badge.getName());
        String safeBadgeType   = HtmlUtils.htmlEscape(badge.getType().name().replace("_", " "));
        String safeBadgeDesc   = HtmlUtils.htmlEscape(badge.getDescription());

        String recognitionSection = "";
        if (recognitionNote != null && !recognitionNote.isBlank()) {
            String safeNote = HtmlUtils.htmlEscape(recognitionNote);
            recognitionSection = "<div class=\"recognition-note\">" + safeNote + "</div>";
        }

        String html = template
                .replace("{{USER_NAME}}", safeName)
                .replace("{{BADGE_NAME}}", safeBadgeName)
                .replace("{{BADGE_TYPE}}", safeBadgeType)
                .replace("{{BADGE_DESCRIPTION}}", safeBadgeDesc)
                .replace("{{RECOGNITION_NOTE_SECTION}}", recognitionSection);

        // Generate PDF certificate
        ByteArrayOutputStream pdfOutputStream = badgePdfGeneratorService.generateBadgeCertificate(
                user.getFirstName() + " " + user.getLastName(),
                badge.getName(),
                badge.getType().name(),
                badge.getDescription(),
                recognitionNote
        );

        // Send email with PDF attachment
        emailService.sendEmailWithAttachment(
                user.getEmail(),
                "You've Earned a New Badge on EduNest!",
                html,
                pdfOutputStream.toByteArray(),
                badge.getName().replace(" ", "_") + "_Certificate.pdf"
        );
    }

    private UserAdminBadgeResponse toUserAdminBadgeDtoFromProjection(UserAdminBadgeDetailProjection projection) {
        return UserAdminBadgeResponse.builder()
                .id(projection.getId())
                .userId(projection.getUserId())
                .userFullName(projection.getUserFullName())
                .badgeId(projection.getBadgeId())
                .badgeName(projection.getBadgeName())
                .badgeDescription(projection.getBadgeDescription())
                .badgeType(AdminBadgeType.valueOf(projection.getBadgeType()))
                .recognitionNote(projection.getRecognitionNote())
                .awardedAt(projection.getAwardedAt())
                .build();
    }
}