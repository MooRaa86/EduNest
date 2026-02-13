package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@SuperBuilder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "weeks")
public class Week extends BaseEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "mentorship_id", nullable = false)
    private MentorShip mentorship;

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL)
    private List<Task> tasks;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL)
    private List<Quiz> quizzes;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL)
    private List<Project> projects;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL)
    private List<Session> liveSessions;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL)
    private List<Lecture> lectures;

}
