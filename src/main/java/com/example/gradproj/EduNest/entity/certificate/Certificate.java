package com.example.gradproj.EduNest.entity.certificate;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "certificates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "mentorship_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class Certificate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private MentorShip mentorship;

    @Column(name = "student_rank", nullable = false)
    private long rank;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;
}
