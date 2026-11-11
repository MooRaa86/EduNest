package com.example.gradproj.EduNest.service.quiz;

import com.example.gradproj.EduNest.config.learning.LateSubmissionProperties;
import com.example.gradproj.EduNest.config.quiz.QuizProperties;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * EB-53 — per-quiz transactional close worker (D-15b Option B: ordinary
 * {@code REQUIRED} propagation invoked by a non-transactional dispatcher).
 *
 * <p>Lock protocol (canonical EB-51 order, serialised against concurrent
 * submissions and mentor status changes):
 * <ol>
 *   <li>L1: week row lock ({@code weekRepository.findForUpdateById})</li>
 *   <li>L2: quiz row lock ({@code quizRepository.findForUpdateById})</li>
 * </ol>
 * State is re-checked under the lock; a quiz still holding an attempt that is
 * eligible to submit ({@code endDate + grace > now}) is skipped, never closed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizCloseSweepWorker {

    private final WeekRepository weekRepository;
    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final LateSubmissionProperties lateSubmissionProperties;

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean closeExpiredQuiz(Long quizId, LocalDateTime now) {
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null) {
            return false;
        }

        weekRepository.findForUpdateById(quiz.getWeek().getId())
                .orElse(null);

        Quiz locked = quizRepository.findForUpdateById(quizId).orElse(null);
        if (locked == null) {
            return false;
        }

        // Double-check guard under lock: state may have changed concurrently
        // (mentor manual close, draft revert, closeAt edit is DRAFT-only).
        if (locked.getStatus() != QuizStatus.PUBLISHED
                || locked.getCloseAt() == null
                || locked.getCloseAt().isAfter(now)) {
            return false;
        }

        // An attempt is still eligible to submit while endDate + grace > now.
        LocalDateTime submissionCutoff = now.minusMinutes(lateSubmissionProperties.getLateGraceMinutes());
        boolean inFlightAttempt = quizSubmissionRepository.existsByQuiz_IdAndEndDateAfter(quizId, submissionCutoff);
        if (inFlightAttempt) {
            log.debug("Quiz id={} past closeAt but an attempt is still within its grace window — skipped", quizId);
            return false;
        }

        locked.setStatus(QuizStatus.CLOSED);
        quizRepository.save(locked);
        log.info("Auto-closed quiz id={} ('{}') after closeAt {}", quizId, locked.getTitle(), locked.getCloseAt());
        return true;
    }
}
