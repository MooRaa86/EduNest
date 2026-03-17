package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionAttendanceResultRepository extends JpaRepository<SessionAttendanceResult, Long> {
    Optional<SessionAttendanceResult> findBySession_IdAndStudent_Id(Long sessionId, Long studentId);
    List<SessionAttendanceResult> findBySession_Id(Long sessionId);
}
