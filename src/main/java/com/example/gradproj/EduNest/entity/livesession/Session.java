package com.example.gradproj.EduNest.entity.livesession;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sessions")
@Setter @Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Session extends BaseEntity {

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotNull(message = "Scheduled date/time cannot be null")
    private LocalDateTime scheduledAt;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id",nullable = false)
    private Week week;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SessionAttendance> attendances = new ArrayList<>();

}
