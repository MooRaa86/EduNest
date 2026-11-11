package com.example.gradproj.EduNest.config.learning;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EB-53 / P4-04 (D-08): shared late-submission grace configuration.
 *
 * <p>A submission (task, project, or quiz attempt) may land up to
 * {@code lateGraceMinutes} after its deadline. Within the grace it is accepted
 * with {@code isLate=true} (quizzes additionally apply the existing -1 pt/min
 * penalty, floored at 0); beyond the grace it is rejected. Late resubmissions
 * stay rejected. Default 10 minutes.
 */
@Component
@ConfigurationProperties(prefix = "app.submission")
@Getter
@Setter
public class LateSubmissionProperties {

    /** Grace window in minutes after a deadline before submissions are rejected. Default 10. */
    private int lateGraceMinutes = 10;
}
