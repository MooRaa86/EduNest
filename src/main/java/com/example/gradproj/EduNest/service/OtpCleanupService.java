package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.repository.OTPRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OtpCleanupService {

    private final OTPRepository otpRepository;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void removeExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpRepository.deleteByExpiresAtBefore(now);
        System.out.println("Expired OTPs cleaned up at: " + now);
    }
}