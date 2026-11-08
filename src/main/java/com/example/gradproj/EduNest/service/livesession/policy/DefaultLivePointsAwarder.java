package com.example.gradproj.EduNest.service.livesession.policy;

import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Active when {@code app.livesession.attendance.enforced=true}.
 *
 * <p>Persists SessionAttendanceResult rows, awards bonus points to Present students,
 * and notifies students of their session results (LS-02 / D-15).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.livesession.attendance.enforced", havingValue = "true")
@RequiredArgsConstructor
public class DefaultLivePointsAwarder implements PointsAwardingPolicy {

    private final EnrollmentRepository enrollmentRepository;
    private final SessionAttendanceResultRepository sessionAttendanceResultRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final NotificationService notificationService;
    private final LiveSessionProperties properties;

    @Override
    @Transactional
    public void awardPoints(Session session, List<AttendanceResponse> results) {
        Long mentorshipId = session.getWeek().getMentorship().getId();

        Map<Long, String> attendanceMap = new HashMap<>();
        if (results != null) {
            for (AttendanceResponse result : results) {
                attendanceMap.put(result.getStudentId(), result.getStatus());
            }
        }

        List<Student> allStudents = enrollmentRepository.findStudentsByMentorshipId(mentorshipId);
        int bonusPoints = properties.getAttendance().getBonusPoints();

                for (Student student : allStudents) {
            String status = attendanceMap.getOrDefault(student.getId(), "Absent");
            boolean attended = "Present".equals(status);

            // EB-51: Idempotency pre-check under lock — avoid duplicate results and points deltas
            var existing = sessionAttendanceResultRepository
                    .findBySession_IdAndStudent_Id(session.getId(), student.getId());
            if (existing.isPresent()) {
                log.warn("Attendance result already recorded for session {} and student {}, skipping duplicate award",
                        session.getId(), student.getId());
                continue;
            }

            SessionAttendanceResult attendanceResult = SessionAttendanceResult.builder()
                    .session(session)
                    .student(student)
                    .attended(attended)
                    .build();

            sessionAttendanceResultRepository.save(attendanceResult);

            int pointsEarned = 0;
            if (attended) {
                pointsEarned = bonusPoints;
                totalPointsService.applyDelta(student, session.getWeek().getMentorship(), pointsEarned);
            }

            try {
                notificationService.sendToUserByEmail(
                        student.getEmail(),
                        "Session Attendance Result",
                        "Your attendance for session " + session.getTitle() + " is: " + status +
                                (attended ? ". You earned " + pointsEarned + " points!" : "."),
                        NotificationType.LIVE_SESSION
                );
            } catch (Exception ex) {
                log.warn("Failed to send attendance notification to {}: {}", student.getEmail(), ex.getMessage());
            }
        }
    }
}
