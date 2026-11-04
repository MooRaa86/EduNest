package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
// [EB-44][P3-02] one enrollment per student per mentorship; the pair index
// idx_enrollments_student_mentorship is replaced by this unique constraint
// (V3 drops the redundant non-unique pair index and adds a mentor-side
// single-column index).
@Table(name = "enrollments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_enrollment_student_mentorship",
                columnNames = {"student_id", "mentorship_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Enrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private MentorShip mentorShip;

    private Double price;

    // [EB-81] PG compatibility: bare `double` is not a PostgreSQL type; `double precision`
    // is portable across PostgreSQL / MySQL / H2 with identical type and default semantics.
    @Builder.Default
    @Column(columnDefinition = "double precision default 0.0")
    private Double platformProfit = 0.0;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
}
