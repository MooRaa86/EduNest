package com.example.gradproj.EduNest.entity.badges;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "badge_awards",
    uniqueConstraints = @UniqueConstraint(columnNames = {"badge_id", "student_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class BadgeAward extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "awarded_by", nullable = false)
    private Mentor awardedBy;

    @Column(columnDefinition = "TEXT")
    private String note;
}
