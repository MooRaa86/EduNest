package com.example.gradproj.EduNest.service.quiz.submission;

import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QuizSubmissionReadTest {
    @Test
    void nullScoreWithoutStartDateRemainsNotSubmittedEvenForEmptyQuiz() {
        var quizzes = mock(QuizRepository.class);
        var submissions = mock(QuizSubmissionRepository.class);
        var security = mock(SecurityService.class);
        var service = new QuizSubmissionServiceImpl(quizzes, submissions, null, null, null, security);
        when(security.getCurrentUserEmail()).thenReturn("owner");
        when(security.isMentorOwnQuiz(1L, "owner")).thenReturn(true);
        when(quizzes.findById(1L)).thenReturn(Optional.of(Quiz.builder().questions(List.of()).build()));
        var student = Student.builder().id(3L).firstName("A").lastName("B").build();
        var attempt = QuizSubmission.builder().student(student).score(null).startDate(null).build();
        when(submissions.findAllByQuiz_Id(1L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(attempt)));

        var result = service.getAllSubmissionsByQuiz(1L, 0, 10);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("Not Submitted");
        assertThat(result.get(0).getScore()).isNull();
        assertThat(result.get(0).getTotalPoints()).isZero();
    }
}
