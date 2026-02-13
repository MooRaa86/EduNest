package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceRepository extends JpaRepository<SessionAttendance, Long> {
    List<SessionAttendance> findBySession_Id(Long sessionId);

}