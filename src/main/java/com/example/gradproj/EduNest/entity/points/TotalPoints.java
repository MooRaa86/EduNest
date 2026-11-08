package com.example.gradproj.EduNest.entity.points;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "total_points",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_mentorship", columnNames = {"student_id", "mentorship_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TotalPoints extends BaseEntity {
    // Null before persist allows Spring Data to recognize a new entity.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private MentorShip mentorship;

    @Builder.Default
    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;
}
