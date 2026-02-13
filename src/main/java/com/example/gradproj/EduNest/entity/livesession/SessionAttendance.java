package com.example.gradproj.EduNest.entity.livesession;

import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_attendance")
@Setter @Getter
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

    private LocalDateTime snapshotTime;
}
