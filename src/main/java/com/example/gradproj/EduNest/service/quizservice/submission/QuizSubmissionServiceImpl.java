package com.example.gradproj.EduNest.service.quizservice.submission;

import com.example.gradproj.EduNest.dto.quizdto.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.entity.Student;
import com.example.gradproj.EduNest.entity.quizentity.Question;
import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.entity.quizentity.QuizSubmission;
import com.example.gradproj.EduNest.entity.quizentity.StudentAnswer;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.StudentRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizSubmissionRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class QuizSubmissionServiceImpl implements QuizSubmissionService {

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final StudentRepository studentRepository;


    private String getCurrentStudentEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }

    @Override
    public QuizSubmissionResponseDTO submitQuizAnswers(
            QuizSubmissionDTO quizSubmissionDTO, Long quizId) {

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        Student student = studentRepository
                .findByEmail(getCurrentStudentEmail());

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Quiz is not available");
        }

        if (quizSubmissionDTO.getAnswers() == null
                || quizSubmissionDTO.getAnswers().isEmpty()) {
            throw new globalLogicEx("Answers are required");
        }

        if (quizSubmissionRepository.existsByStudent_IdAndQuiz_Id(student.getId(), quizId)) {
            throw new globalLogicEx("Quiz already submitted");
        }
        Map<Long, Question> questions =
                quiz.getQuestions().stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));

        ScoreResult score = calculateScore(quizSubmissionDTO.getAnswers(), questions);

        QuizSubmission quizSubmission = QuizSubmission.builder()
                .quiz(quiz)
                .student(student)
                .score(score.getTotalScore())
                .submittedAt(LocalDateTime.now())
                .build();


        List<StudentAnswer> studentAnswers =
                quizSubmissionDTO.getAnswers().stream()
                        .map(dto -> {
                            Question question = questions.get(dto.getQuestionId());
                            if (question == null) {
                                throw new globalLogicEx("Question with id " + dto.getQuestionId() + " does not exist in this quiz");
                            }
                        return StudentAnswer.builder()
                                .submission(quizSubmission)
                                .question(question)
                                .selectedAnswer(dto.getSelectedAnswer())
                                .build();
    })
                        .toList();

        quizSubmission.setAnswers(studentAnswers);

        quizSubmissionRepository.save(quizSubmission);

        return QuizSubmissionResponseDTO.builder()
                .id(quizSubmission.getId())
                .studentId(student.getId())
                .quizId(quiz.getId())
                .score(score.getTotalScore())
                .submittedAt(quizSubmission.getSubmittedAt())
                .build();
    }

    public List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId){
        QuizSubmission submission = quizSubmissionRepository
                .findByStudent_IdAndQuiz_Id(studentId, quizId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));

        return submission.getAnswers().stream()
                .map(ans -> StudentAnswerDTO.builder()
//                        .submissionId(ans.getSubmission().getId())
                        .questionId(ans.getQuestion().getId())
                        .selectedAnswer(ans.getSelectedAnswer())
                        .build())
                .toList();
    }

  public  List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId, int page, int size){
        return quizSubmissionRepository.findAllByStudent_Id(studentId , PageRequest.of(page, size))
                .stream()
                .map(sub -> QuizSubmissionResponseDTO.builder()
                        .id(sub.getId())
                        .studentId(studentId)
                        .quizId(sub.getQuiz().getId())
                        .score(sub.getScore())
                        .submittedAt(sub.getSubmittedAt())
                        .build())
                .toList();
    }

    @Override
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId, int page, int size){
        return quizSubmissionRepository.findAllByQuiz_Id(quizId,PageRequest.of(page, size))
                .stream()
                .map(sub -> QuizSubmissionResponseDTO.builder()
                        .id(sub.getId())
                        .studentId(sub.getStudent().getId())
                        .quizId(sub.getQuiz().getId())
                        .score(sub.getScore())
                        .submittedAt(sub.getSubmittedAt())
                        .build())
                .toList();
    }


    private ScoreResult calculateScore(List<StudentAnswerDTO> answers, Map<Long, Question> questions) {
        int score = 0;
        int numOfCorrect = 0;

        for (StudentAnswerDTO studentAnswer : answers) {
            Question question = questions.get(studentAnswer.getQuestionId());
            if (question == null) continue;

            String correctAnswer= String.valueOf(question.getCorrectAnswer());
            if (studentAnswer.getSelectedAnswer().equals(correctAnswer)) {
                score += question.getPoints();
                numOfCorrect++;
            }
        }

        return new ScoreResult(score, numOfCorrect);
    }

    @Getter
    public static class ScoreResult {
        private int totalScore;
        private int numCorrect;

        public ScoreResult(int totalScore, int numCorrect) {
            this.totalScore = totalScore;
            this.numCorrect = numCorrect;
        }

    }

}


