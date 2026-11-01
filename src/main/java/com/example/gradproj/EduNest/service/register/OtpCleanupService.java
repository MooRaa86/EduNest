package com.example.gradproj.EduNest.service.register;

import com.example.gradproj.EduNest.repository.OTPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpCleanupService {

    private final OTPRepository otpRepository;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void removeExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpRepository.deleteByExpiresAtBefore(now);
        log.info("Expired OTPs cleaned up at: {}", now);
    }
}
