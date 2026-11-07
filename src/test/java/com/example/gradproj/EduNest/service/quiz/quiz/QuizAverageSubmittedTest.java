package com.example.gradproj.EduNest.service.quiz.quiz;

import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAverageSubmittedTest {
    private final QuizServiceImpl service = new QuizServiceImpl(null, null, null, null, null, null, null);

    private double average(QuizSubmission... submissions) {
        Quiz quiz = Quiz.builder().submissions(List.of(submissions)).build();
        return (double) ReflectionTestUtils.invokeMethod(service, "calculateAverageScore", quiz);
    }

    @Test
    void ignoresOpenedAttemptButIncludesSubmittedZero() {
        assertThat(average(QuizSubmission.builder().score(null).build(),
                QuizSubmission.builder().score(0.0).build(),
                QuizSubmission.builder().score(10.0).build())).isEqualTo(5.0);
    }

    @Test
    void emptyOrOpenedOnlyHasZeroAverage() {
        assertThat(average()).isZero();
        assertThat(average(QuizSubmission.builder().score(null).build())).isZero();
    }
}
