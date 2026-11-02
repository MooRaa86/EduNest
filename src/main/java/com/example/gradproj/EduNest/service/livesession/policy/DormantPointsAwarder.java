package com.example.gradproj.EduNest.service.livesession.policy;

import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default active policy when {@code app.livesession.attendance.enforced=false} (or missing).
 *
 * <p>Per Decision D-15: points awarding and absence tracking are dormant (no-op).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.livesession.attendance.enforced", havingValue = "false", matchIfMissing = true)
public class DormantPointsAwarder implements PointsAwardingPolicy {

    @Override
    public void awardPoints(Session session, List<AttendanceResponse> results) {
        log.info("Points awarding is dormant per D-15 for session {}. Zero gamification writes performed.", session.getId());
    }
}
