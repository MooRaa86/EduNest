package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionAttendanceResultRepository extends JpaRepository<SessionAttendanceResult, Long> {
    Optional<SessionAttendanceResult> findBySession_IdAndStudent_Id(Long sessionId, Long studentId);
    List<SessionAttendanceResult> findBySession_Id(Long sessionId);

    @Query("""
        SELECT r
        FROM SessionAttendanceResult r
        JOIN r.session s
        WHERE r.student.id = :studentId
          AND s.week.id = :weekId
          AND r.attended = true
    """)
    List<SessionAttendanceResult> findAttendedByStudentIdAndWeekId(@Param("studentId") Long studentId,
                                                                    @Param("weekId") Long weekId);

    @Query("""
        SELECT r
        FROM SessionAttendanceResult r
        JOIN r.session s
        WHERE r.student.id = :studentId
          AND s.week.id IN :weekIds
          AND r.attended = true
    """)
    List<SessionAttendanceResult> findByStudent_IdAndSession_Week_IdIn(@Param("studentId") Long studentId,
                                                                          @Param("weekIds") List<Long> weekIds);
}
