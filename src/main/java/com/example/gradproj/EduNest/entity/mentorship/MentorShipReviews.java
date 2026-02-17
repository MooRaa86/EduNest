package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Student;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
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
        indexes = {
                @Index(name = "idx_mentorship_only", columnList = "mentorship_id"),
                @Index( name = "idx_mentorship_student", columnList = "mentorship_id, student_id")
        }
)
public class MentorShipReviews extends BaseEntity {

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
