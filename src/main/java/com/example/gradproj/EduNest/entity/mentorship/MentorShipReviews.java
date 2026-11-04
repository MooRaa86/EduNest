package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Student;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Entity
@SuperBuilder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "mentorship_reviews",
        // [EB-44][P3-02] unique per student per mentorship; the redundant
        // non-unique pair index idx_mentorship_student was removed (V3),
        // single-column mentorship index kept for FK/mentor-scoped queries.
        indexes = {
                @Index(name = "idx_mentorship_only", columnList = "mentorship_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mentorship_review_student",
                        columnNames = {"student_id", "mentorship_id"})
        }
)
public class MentorShipReviews extends BaseEntity {

    @Size(max = 500, message = "Feedback must not exceed 500 characters")
    private String feedBack;

    long rating;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_id")
    private MentorShip mentorShip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

}
