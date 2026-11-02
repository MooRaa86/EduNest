package com.example.gradproj.EduNest.service.livesession.policy;

import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Default active policy when {@code app.livesession.attendance.enforced=false} (or missing).
 *
 * <p>Per Decision D-15: attendance evaluation is suspended/dormant; no verdicts or absences are enforced.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.livesession.attendance.enforced", havingValue = "false", matchIfMissing = true)
public class NoEnforcementAttendancePolicy implements AttendancePolicy {

    @Override
    public List<AttendanceResponse> evaluateAttendance(Session session, List<SessionAttendance> snapshots) {
        log.info("Attendance evaluation is dormant (D-15) for session {}. Snapshots recorded: {}",
                session.getId(), snapshots != null ? snapshots.size() : 0);
        return Collections.emptyList();
    }
}
