package com.example.gradproj.EduNest.entity.lectures;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Setter @Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@SuperBuilder
@Table(name = "lectures")
public class Lecture extends BaseEntity {
    @Column(name = "lecture_title")
    private String title;

    @Column(name = "lecture_url")
    private String lectureUrl;

    @ManyToOne
    @JoinColumn(name = "week_id")
    private Week week;
}
