package com.example.gradproj.EduNest.service.quiz;

import lombok.extern.slf4j.Slf4j;import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EB-53 / P4-04 (LEARN-10 / D-08): non-transactional background dispatcher that
 * auto-closes PUBLISHED quizzes whose explicit {@code closeAt} deadline has
 * passed once no eligible attempt remains.
 *
 * <p>Follows the D-15b Option B architecture (same as {@code LiveSessionSweepService}):
 * each candidate quiz is dispatched to {@link QuizCloseSweepWorker} in its own
 * transaction, so one failure never aborts the sweep batch and every close is
 * idempotent (status re-checked under the week -> quiz lock).
 *
 * <p>Legacy quizzes with {@code closeAt == NULL} are never selected — they keep
 * their manual-close-only behavior and no historical deadline is invented.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.quiz.auto-close.enabled", havingValue = "true", matchIfMissing = true)
public class QuizCloseSweepService {

    private final com.example.gradproj.EduNest.repository.quiz.QuizRepository quizRepository;
    private final QuizCloseSweepWorker sweepWorker;

    private Clock clock = Clock.systemDefaultZone();

    @Autowired
    public QuizCloseSweepService(
            com.example.gradproj.EduNest.repository.quiz.QuizRepository quizRepository,
            QuizCloseSweepWorker sweepWorker
    ) {
        this.quizRepository = quizRepository;
        this.sweepWorker = sweepWorker;
    }

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Scheduled(fixedDelayString = "${app.quiz.auto-close.sweep-rate-ms:300000}")
    public void sweep() {
        int closed = autoCloseExpiredQuizzes(LocalDateTime.now(clock));
        if (closed > 0) {
            log.info("Quiz auto-close sweep completed: {} quizzes closed", closed);
        }
    }

    /** Visible for tests: drives the sweep with an explicit clock reading. */
    public int autoCloseExpiredQuizzes(LocalDateTime now) {
        List<Long> candidateIds = quizRepository.findIdsDueForAutoClose(
                com.example.gradproj.EduNest.enums.quiz.QuizStatus.PUBLISHED, now);
        int closedCount = 0;
        for (Long quizId : candidateIds) {
            try {
                if (sweepWorker.closeExpiredQuiz(quizId, now)) {
                    closedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to auto-close quiz id={}: {}", quizId, e.getMessage(), e);
            }
        }
        return closedCount;
    }
}
