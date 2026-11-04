package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissionRepository;

    public Double getCommissionRate() {
        return commissionRepository.findTopByOrderByCreatedAtDesc()
                .map(Commission::getRate)
                .filter(rate -> rate != null && rate >= 0.0 && rate <= 100.0)
                .orElseThrow(() -> new globalLogicEx("Commission rate not set"));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void setCommissionRate(Double rate) {
        if (rate == null || rate < 0 || rate > 100) {
            throw new globalLogicEx("Commission rate must be between 0 and 100");
        }

        Commission commission = Commission.builder()
                .rate(rate)
                .build();

        commissionRepository.save(commission);
    }

    public Double calculatePlatformCut(Double price) {
        Double rate = getCommissionRate();
        if (price == null || price <= 0.0) {
            return 0.0;
        }
        return price * (rate / 100.0);
    }

    public Double calculateMentorPayout(Double price) {
        if (price == null || price <= 0.0) {
            return 0.0;
        }
        return price - calculatePlatformCut(price);
    }
}
