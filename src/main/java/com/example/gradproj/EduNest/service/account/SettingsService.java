package com.example.gradproj.EduNest.service.account;

import com.example.gradproj.EduNest.dto.account.request.ChangeEmailRequest;
import com.example.gradproj.EduNest.dto.account.request.ChangePasswordRequest;
import com.example.gradproj.EduNest.dto.account.request.UpdateSettingsRequest;
import com.example.gradproj.EduNest.dto.account.response.SettingsResponse;
import com.example.gradproj.EduNest.entity.account.Settings;
import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.account.ThemeMode;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.OTPRepository;
import com.example.gradproj.EduNest.repository.account.SettingsRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.jwt.JwtService;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OTPRepository otpRepository;
    private final EmailService emailService;
    private final JwtService jwtService;

    private final int expiryTime = 2;


    @Transactional
    public SettingsResponse changeSettings(UpdateSettingsRequest request) {

        UserEntity user = getCurrentUser();

        Settings settings = settingsRepository.findByUser(user)
                .orElseGet(this::setDefaultSettings);

        if (request.getEmailNotifications() != null)
            settings.setEmailNotifications(request.getEmailNotifications());

        if (request.getPushNotifications() != null)
            settings.setPushNotifications(request.getPushNotifications());

        if (request.getWeeklyDigest() != null)
            settings.setWeeklyDigest(request.getWeeklyDigest());

        if (request.getMessageNotifications() != null)
            settings.setMessageNotifications(request.getMessageNotifications());

        if (request.getMode() != null)
            settings.setMode(request.getMode());

        settingsRepository.save(settings);

        return buildResponse(settings);
    }

    @Transactional
    public Settings setDefaultSettings() {

        UserEntity user = getCurrentUser();

        Settings settings = Settings.builder()
                .user(user)
                .emailNotifications(true)
                .pushNotifications(true)
                .weeklyDigest(true)
                .messageNotifications(true)
                .mode(ThemeMode.LIGHT)
                .build();

        return settingsRepository.save(settings);
    }

    public SettingsResponse getMySettings() {

        UserEntity user = getCurrentUser();

        Settings settings = settingsRepository.findByUserEmail(user.getEmail())
                .orElseGet(this::setDefaultSettings);

        return buildResponse(settings);
    }

    private SettingsResponse buildResponse(Settings settings) {
        return SettingsResponse.builder()
                .email(settings.getUser().getEmail())
                .emailNotifications(settings.isEmailNotifications())
                .pushNotifications(settings.isPushNotifications())
                .weeklyDigest(settings.isWeeklyDigest())
                .messageNotifications(settings.isMessageNotifications())
                .mode(settings.getMode().name())
                .build();
    }


    @Transactional
    public void changeEmail(ChangeEmailRequest request) {

        UserEntity user = getCurrentUser();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new globalLogicEx("Password is incorrect");
        }

        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new globalLogicEx("Email already in use");
        }

        user.setEmail(request.getNewEmail());
        userRepository.save(user);
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
    public void deleteAccount() {

        UserEntity user = getCurrentUser();

        otpRepository.deleteByUserAndOtpType(user, OtpType.DELETE);

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
            throw new globalLogicEx("OTP expired");
        }

        otpRepository.deleteByUserAndOtpType(user, OtpType.DELETE);

        settingsRepository.deleteByUser(user);

        userRepository.delete(user);
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
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }
}