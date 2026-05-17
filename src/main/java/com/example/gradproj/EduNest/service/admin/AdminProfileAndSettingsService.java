package com.example.gradproj.EduNest.service.admin;


import com.example.gradproj.EduNest.dto.account.request.ChangePasswordRequest;
import com.example.gradproj.EduNest.dto.profile.request.UpdateAdminProfileRequest;
import com.example.gradproj.EduNest.dto.profile.response.AdminProfileInformationResponse;
import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.OTPRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.mentorShip.CommissionService;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminProfileAndSettingsService {

    private final AdminRepository adminRepository;
    private final ImageStorageService imageStorageService;
    private final PasswordEncoder passwordEncoder;
    private final OTPRepository otpRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final CommissionService commissionService;
    private final int expiryTime = 2;

    private static final String admin_IMAGE_FOLDER = "admin";


    public AdminProfileInformationResponse getAdminProfileInfo() {

        Admin admin = getCurrentadmin();

        return AdminProfileInformationResponse.builder()
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .fullName(admin.getFirstName() + " " + admin.getLastName())
                .email(admin.getEmail())
                .headline(admin.getHeadline())
                .profileImageUrl(admin.getProfilePicture())
                .commissionRate(commissionService.getCommissionRate())
                .build();

    }

    @Transactional
    public void updateAdminProfileInfo(UpdateAdminProfileRequest request) {
        Admin admin = getCurrentadmin();
        if (request.getFirstName() != null) admin.setFirstName(request.getFirstName());
        if (request.getLastName() != null) admin.setLastName(request.getLastName());
        if (request.getHeadline() != null) admin.setHeadline(request.getHeadline());

        adminRepository.save(admin);
    }


    public String updateProfileImage(MultipartFile image) {
        Admin admin = getCurrentadmin();

        if (image != null && !image.isEmpty()) {
            imageStorageService.deleteImage(admin_IMAGE_FOLDER, admin.getProfilePicture());
            String newImageUrl = imageStorageService.saveImage(admin_IMAGE_FOLDER, admin.getId(), image);
            admin.setProfilePicture(newImageUrl);
            adminRepository.save(admin);
            return newImageUrl;
        }
        throw new globalLogicEx("Image is empty");
    }

    @Transactional
    public void requestChangeEmail(String newEmail) {

        Admin admin = getCurrentadmin();

        if (admin.getEmail().equals(newEmail)) {
            throw new globalLogicEx("You are already using this email");
        }

        if (adminRepository.existsByEmail(newEmail)) {
            throw new globalLogicEx("Email already in use");
        }

        if(userRepository.existsByEmail(newEmail)){
            throw new globalLogicEx("Email is already in use in other role get out from here !!");
        }

        otpRepository.deleteByAdminAndOtpType(admin, OtpType.CHANGE_EMAIL);
        otpRepository.flush();
        SecureRandom random = new SecureRandom();
        String otpCode = String.valueOf(100000 + random.nextInt(900000));

        OTP otp = OTP.builder()
                .otpCode(otpCode)
                .admin(admin)
                .otpType(OtpType.CHANGE_EMAIL)
                .pendingEmail(newEmail)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryTime))
                .build();

        otpRepository.save(otp);

        String template = emailService.getEmailTemplate("change-email.html");

        String html = template
                .replace("{{otp}}", otpCode)
                .replace("{{name}}", admin.getFirstName())
                .replace("{{minutes}}", String.valueOf(expiryTime));

        emailService.sendEmail(
                newEmail,
                "Change Your Email",
                html
        );
    }

    @Transactional
    public void confirmChangeEmail(String otpCode) {
        Admin admin = getCurrentadmin();

        OTP otp = otpRepository
                .findByAdminAndOtpCodeAndOtpType(admin, otpCode, OtpType.CHANGE_EMAIL)
                .orElseThrow(() -> new globalLogicEx("Invalid OTP"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new globalLogicEx("OTP expired");
        }

        admin.setEmail(otp.getPendingEmail());
        adminRepository.save(admin);
        otpRepository.deleteByAdminAndOtpType(admin, OtpType.CHANGE_EMAIL);
        SecurityContextHolder.clearContext();

    }
    @Transactional
    public void changePassword(ChangePasswordRequest request) {

        Admin admin = getCurrentadmin();

        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPassword())) {
            throw new globalLogicEx("Old password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new globalLogicEx("New password and confirm password do not match");
        }

        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        adminRepository.save(admin);
    }

    private Admin getCurrentadmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return adminRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("admin not found"));
    }

}
