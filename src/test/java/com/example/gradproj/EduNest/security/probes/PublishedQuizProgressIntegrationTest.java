package com.example.gradproj.EduNest.security.probes;

import java.util.UUID;

import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.homepage.HomePageService;
import com.example.gradproj.EduNest.service.mylearning.MyLearningService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedQuizProgressIntegrationTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weeks;
    @Autowired private QuizRepository quizzes;
    @Autowired private QuizSubmissionRepository submissions;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    @Autowired private HomePageService homepage;
    @Autowired private MyLearningService myLearning;

    private Quiz quiz(Week week, String title, QuizStatus status) {
        return quizzes.saveAndFlush(Quiz.builder().week(week).title(title).description("test")
                .durationMinutes(10).status(status).build());
    }

    private void attempt(Student student, Quiz quiz, Double score) {
        submissions.saveAndFlush(QuizSubmission.builder().student(student).quiz(quiz).score(score).build());
    }

    @Test
    void currentProgressAndOrderingCountOnlySubmittedPublishedQuizzes() {
        String email = "eb54-progress-student@example.org";
        var a = auth.mentorship("eb54-progress-mentor@example.org", Status.ACTIVE);
        var b = auth.mentorship("eb54-progress-mentor@example.org", Status.ACTIVE);
        var empty = auth.mentorship("eb54-progress-mentor@example.org", Status.ACTIVE);
        Student student = auth.enroll(a.mentorship().getId(), email).getStudent();
        auth.enroll(b.mentorship().getId(), email);
        auth.enroll(empty.mentorship().getId(), email);
        Week wa = weeks.saveAndFlush(Week.builder().mentorship(a.mentorship()).title("week a").build());
        Week wb = weeks.saveAndFlush(Week.builder().mentorship(b.mentorship()).title("week b").build());
        attempt(student, quiz(wa, "opened", QuizStatus.PUBLISHED), null);
        attempt(student, quiz(wa, "zero", QuizStatus.PUBLISHED), 0.0);
        attempt(student, quiz(wa, "positive", QuizStatus.PUBLISHED), 8.0);
        attempt(student, quiz(wa, "unpublished", QuizStatus.DRAFT), 9.0);
        attempt(student, quiz(wb, "completed", QuizStatus.PUBLISHED), 1.0);
        // The extra joined rows must not multiply quiz counts.
        transactions.executeWithoutResult(status -> {
            entityManager.persist(Lecture.builder().week(wa).title("lecture 1").build());
            entityManager.persist(Lecture.builder().week(wa).title("lecture 2").build());
        });

        var content = enrollments.getContentProgress(a.mentorship().getId(), email);
        assertThat(content.getTotalQuizzes()).isEqualTo(3);
        assertThat(content.getCompletedQuizzes()).isEqualTo(2);
        assertThat(homepage.getStudentMentorshipProgress(email, a.mentorship().getId()).getProgressPercentage()).isEqualTo(66);
        var progress = enrollments.findAllEnrolledMentorshipsWithProgress(email, PageRequest.of(0, 10));
        var rowA = progress.stream().filter(p -> p.getMentorshipId().equals(a.mentorship().getId())).findFirst().orElseThrow();
        assertThat(rowA.getTotalQuizzes()).isEqualTo(3);
        assertThat(rowA.getSubmittedQuizzes()).isEqualTo(2);
        var emptyRow = progress.stream().filter(p -> p.getMentorshipId().equals(empty.mentorship().getId())).findFirst().orElseThrow();
        assertThat(emptyRow.getTotalQuizzes()).isZero();
        assertThat(emptyRow.getSubmittedQuizzes()).isZero();
        assertThat(progress.getContent().indexOf(rowA)).isLessThan(
                progress.getContent().indexOf(progress.stream().filter(p -> p.getMentorshipId().equals(b.mentorship().getId())).findFirst().orElseThrow()));
        var continuation = enrollments.findContinueLearningWithProgress(email, 10);
        assertThat(continuation.get(0).getMentorshipId()).isEqualTo(a.mentorship().getId());
        assertThat(continuation.get(0).getCompletedQuizzes()).isEqualTo(2);
        var dto = myLearning.getMyLearning(email, 0, 10).getMentorships().getContent().stream()
                .filter(p -> p.getMentorshipId().equals(a.mentorship().getPublicId())).findFirst().orElseThrow();
        assertThat(dto.getProgressPercentage()).isEqualTo(66);
    }
}
