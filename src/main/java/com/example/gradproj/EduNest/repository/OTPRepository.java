package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.register.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {

    Optional<OTP> findByUser_EmailAndOtpCode(String email, String otpCode);

    Optional<OTP> findByUser_Email(String email);

    void deleteByExpiresAtBefore(LocalDateTime now);

    void deleteByUserAndOtpType(UserEntity user, OtpType otpType);

    Optional<OTP> findByUserAndOtpCodeAndOtpType(UserEntity user, String otpCode, OtpType otpType);

    Optional<OTP> findByUserAndOtpType(UserEntity user, OtpType type);
}

