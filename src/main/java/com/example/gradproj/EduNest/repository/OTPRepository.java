package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.register.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {

    List<OTP> findAllByUser_EmailAndOtpType(String email, OtpType otpType);

    void deleteByExpiresAtBefore(LocalDateTime now);

    void deleteByUserAndOtpType(UserEntity user, OtpType otpType);

    Optional<OTP> findByUserAndOtpCodeAndOtpType(UserEntity user, String otpCode, OtpType otpType);

    Optional<OTP> findByUserAndOtpType(UserEntity user, OtpType type);

    void deleteByAdminAndOtpType(Admin admin, OtpType otpType);
    Optional<OTP> findByAdminAndOtpCodeAndOtpType(Admin admin, String otpCode, OtpType otpType);

}

