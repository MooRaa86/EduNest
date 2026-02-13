package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.register.OTP;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {

    Optional<OTP> findByUser_EmailAndOtpCode(String email, String otpCode);

    Optional<OTP> findByUser_Email(String email);

    void deleteByExpiresAtBefore(LocalDateTime now);
}

