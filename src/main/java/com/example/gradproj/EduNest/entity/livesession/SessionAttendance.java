package com.example.gradproj.EduNest.entity.livesession;

import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_attendance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAttendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
