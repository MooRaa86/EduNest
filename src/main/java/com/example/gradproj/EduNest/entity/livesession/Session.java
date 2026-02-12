package com.example.gradproj.EduNest.entity.livesession;

import com.example.gradproj.EduNest.entity.weeks.MentorShipWeek;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotNull(message = "Scheduled date/time cannot be null")
    @Future(message = "Scheduled date/time must be in the future")
    private LocalDateTime scheduledAt;

    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id",nullable = false)
    private MentorShipWeek week;

}
