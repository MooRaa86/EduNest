package com.example.gradproj.EduNest.service.livesession.policy;

import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;
import com.example.gradproj.EduNest.entity.users.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Active when {@code app.livesession.attendance.enforced=true}.
 *
 * <p>Evaluates attendance using the 5-minute snapshot grid and 75% threshold (LS-02 / D-15).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.livesession.attendance.enforced", havingValue = "true")
@RequiredArgsConstructor
public class SnapshotGridAttendancePolicy implements AttendancePolicy {

    private final LiveSessionProperties properties;

    @Override
    public List<AttendanceResponse> evaluateAttendance(Session session, List<SessionAttendance> snapshots) {
        if (session.getActualStartTime() == null || session.getActualEndTime() == null) {
            log.warn("Cannot evaluate attendance for session {} with missing start/end times", session.getId());
            return Collections.emptyList();
        }

        long sessionDuration = Duration.between(session.getActualStartTime(), session.getActualEndTime()).toMinutes();
        int gridMinutes = Math.max(1, properties.getAttendance().getSnapshotGridMinutes());
        long totalSnapshots = Math.max(1, sessionDuration / gridMinutes);

        Map<Student, Long> studentAttendances = new HashMap<>();
        if (snapshots != null) {
            for (SessionAttendance sessionAttendance : snapshots) {
                Student student = sessionAttendance.getStudent();
                studentAttendances.put(student, studentAttendances.getOrDefault(student, 0L) + 1);
            }
        }

        double passThreshold = properties.getAttendance().getPassThreshold();
        List<AttendanceResponse> attendanceReport = new ArrayList<>();
        for (Map.Entry<Student, Long> entry : studentAttendances.entrySet()) {
            Student student = entry.getKey();
            Long studentSnapshots = entry.getValue();

            double attendancePercentage = (studentSnapshots * 100.0) / totalSnapshots;
            String status = attendancePercentage >= passThreshold ? "Present" : "Absent";

            attendanceReport.add(
                    AttendanceResponse.builder()
                            .sessionId(session.getId())
                            .studentId(student.getId())
                            .attendancePercentage(attendancePercentage)
                            .status(status)
                            .build()
            );
        }

        return attendanceReport;
    }
}
