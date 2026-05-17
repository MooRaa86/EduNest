package com.example.gradproj.EduNest.service.account;

import com.example.gradproj.EduNest.dto.account.request.ChangePasswordRequest;
import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.OTPRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OTPRepository otpRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final int expiryTime = 2;

    @Transactional
    public void requestChangeEmail(String newEmail) {

        UserEntity user = getCurrentUser();

        if (user.getEmail().equals(newEmail)) {
            throw new globalLogicEx("You are already using this email");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new globalLogicEx("Email already in use");
        }

        otpRepository.deleteByUserAndOtpType(user, OtpType.CHANGE_EMAIL);
        otpRepository.flush();
        String otpCode = generateOtp();

        OTP otp = OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .otpType(OtpType.CHANGE_EMAIL)
                .pendingEmail(newEmail)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryTime))
                .build();

        otpRepository.save(otp);

        String template = emailService.getEmailTemplate("change-email.html");

        String html = template
                .replace("{{otp}}", otpCode)
                .replace("{{name}}", user.getFirstName())
                .replace("{{minutes}}", String.valueOf(expiryTime));

        emailService.sendEmail(
                newEmail,
                "Change Your Email",
                html
        );
    }

    @Transactional
    public void confirmChangeEmail(String otpCode) {
        UserEntity user = getCurrentUser();

        OTP otp = otpRepository
                .findByUserAndOtpCodeAndOtpType(user, otpCode, OtpType.CHANGE_EMAIL)
                .orElseThrow(() -> new globalLogicEx("Invalid OTP"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new globalLogicEx("OTP expired");
        }

        user.setEmail(otp.getPendingEmail());
        userRepository.save(user);
        otpRepository.deleteByUserAndOtpType(user, OtpType.CHANGE_EMAIL);
        SecurityContextHolder.clearContext();
    }


    @Transactional
    public void changePassword(ChangePasswordRequest request) {

        UserEntity user = getCurrentUser();

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new globalLogicEx("Old password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new globalLogicEx("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }


    @Transactional
    public void deactivateAccount(String password) {
        UserEntity user = getCurrentUser();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new globalLogicEx("Password is incorrect");
        }

        user.setEnabled(false);
        userRepository.save(user);

        SecurityContextHolder.clearContext();
    }

    @Transactional
    public void deleteAccount() {

        UserEntity user = getCurrentUser();

        otpRepository.deleteByUserAndOtpType(user, OtpType.DELETE);
        otpRepository.flush();
        String otpCode = generateOtp();

        OTP otp = OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .otpType(OtpType.DELETE)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryTime))
                .build();

        otpRepository.save(otp);

        String template = emailService.getEmailTemplate("delete-account.html");

        String html = template
                .replace("{{otp}}", otpCode)
                .replace("{{name}}", user.getFirstName())
                .replace("{{minutes}}", String.valueOf(expiryTime));

        emailService.sendEmail(
                user.getEmail(),
                "Confirm Account Deletion",
                html
        );
    }

    @Transactional
    public void confirmDeleteAccount(String otpCode) {

        UserEntity user = getCurrentUser();

        OTP otp = otpRepository
                .findByUserAndOtpCodeAndOtpType(user, otpCode, OtpType.DELETE)
                .orElseThrow(() -> new globalLogicEx("Invalid OTP"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new globalLogicEx("OTP expired");
        }

        otpRepository.deleteByUserAndOtpType(user, OtpType.DELETE);

        user.setDeleted(Boolean.TRUE);
        userRepository.save(user);
    }


    private UserEntity getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new globalLogicEx("Unauthenticated user");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new globalLogicEx("User not found"));
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}