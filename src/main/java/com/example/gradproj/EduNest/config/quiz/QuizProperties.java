package com.example.gradproj.EduNest.config.quiz;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EB-53 / P4-04 (D-08): late-submission and quiz auto-close configuration.
 *
 * <p>Late policy: first submissions within {@code lateGraceMinutes} after the
 * deadline are accepted with {@code isLate=true} (quiz score additionally
 * penalised by the existing -1 pt/min rule, floored at 0); submissions beyond
 * the grace are rejected. Late resubmissions stay rejected.
 *
 * <p>Auto-close sweep follows the D-15b Option B architecture (non-transactional
 * dispatcher + per-quiz transactional worker) and closes PUBLISHED quizzes whose
 * {@code closeAt} deadline has passed once no eligible attempt remains.
 */
@Component
@ConfigurationProperties(prefix = "app.quiz")
@Getter
@Setter
public class QuizProperties {

    /** Grace window in minutes after a deadline before submissions are rejected. Default 10. */
    private int lateGraceMinutes = 10;

    private AutoClose autoClose = new AutoClose();

    @Getter
    @Setter
    public static class AutoClose {
        /** Master switch for the quiz auto-close sweep. Default true. */
        private boolean enabled = true;

        /** Execution interval in milliseconds for the close sweep. Default 300_000 (5 mins). */
        private long sweepRateMs = 300_000L;
    }
}
