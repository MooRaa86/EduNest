package com.example.gradproj.EduNest.config.livesession;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * P4-08 (EB-57) &amp; D-15 / D-29 Configuration for Live Session auto-lifecycle and attendance.
 *
 * <p>Auto-close sweep is ACTIVE by default (5-minute sweep interval per Omar's directive,
 * 10-minute grace period, 40-minute max duration aligned with Zoom Basic plan D-29).
 *
 * <p>Attendance enforcement &amp; gamification points awarding are DORMANT by default per D-15.
 * Setting {@code app.livesession.attendance.enforced=true} dynamically activates
 * the snapshot grid attendance evaluation and points distribution without any code refactoring.
 */
@Component
@ConfigurationProperties(prefix = "app.livesession")
@Getter
@Setter
public class LiveSessionProperties {

    private AutoClose autoClose = new AutoClose();
    private Attendance attendance = new Attendance();

    @Getter
    @Setter
    public static class AutoClose {
        /** Master switch for auto-close / auto-cancel scheduled sweep. Default true. */
        private boolean enabled = true;

        /** Grace period in minutes after max duration before closing or cancelling. Default 10. */
        private int graceMinutes = 10;

        /** Expected session duration cap in minutes (aligned with Basic plan cap). Default 40. */
        private int maxDurationMinutes = 40;

        /** Execution interval in milliseconds for the auto-lifecycle sweep. Default 300_000 (5 mins). */
        private long sweepRateMs = 300_000L;
    }

    @Getter
    @Setter
    public static class Attendance {
        /** Master switch for attendance enforcement and gamification points awarding. Default false (dormant). */
        private boolean enforced = false;

        /** Minimum attendance percentage required to be marked 'Present'. Default 75.0. */
        private double passThreshold = 75.0;

        /** Snapshot grid interval in minutes for calculating expected snapshot count. Default 5. */
        private int snapshotGridMinutes = 5;

        /** Bonus gamification points awarded to 'Present' students at session end. Default 5. */
        private int bonusPoints = 5;

        /** Minimum penalty/absence floor. Default 0. */
        private int absenceFloor = 0;
    }
}
