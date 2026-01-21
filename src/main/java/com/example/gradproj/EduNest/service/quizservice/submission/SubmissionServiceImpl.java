package com.example.gradproj.EduNest.service.quizservice.submission;

import ch.qos.logback.core.joran.sanity.Pair;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.entity.Student;
import com.example.gradproj.EduNest.entity.quizentity.Question;
import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.entity.quizentity.QuizSubmission;
import com.example.gradproj.EduNest.entity.quizentity.StudentAnswer;
import com.example.gradproj.EduNest.enums.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.StudentRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;



import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final StudentRepository studentRepository;

    @Override
    public QuizSubmissionResponseDTO submitQuizAnswers(
            QuizSubmissionDTO quizSubmissionDTO, Long quizId) {

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        Student student = studentRepository.findById(quizSubmissionDTO.getStudentId())
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        if (!quiz.getStatus().equals(QuizStatus.PUBLISHED)
                ||!quiz.getDeadline().isAfter(LocalDate.now())) {
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
                        .map(dto -> StudentAnswer.builder()
                                .submission(quizSubmission)
                                .question(questions.get(dto.getQuestionId()))
                                .selectedAnswer(dto.getSelectedAnswer())
                                .build())
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

    public List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId) {
        QuizSubmission submission = quizSubmissionRepository
                .findByStudent_IdAndQuiz_Id(studentId, quizId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));

        return submission.getAnswers().stream()
                .map(ans -> StudentAnswerDTO.builder()
                        .questionId(ans.getQuestion().getId())
                        .selectedAnswer(ans.getSelectedAnswer())
                        .build())
                .toList();
    }

    public List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId) {
        return quizSubmissionRepository.findAllByStudent_Id(studentId)
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
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId) {
        return quizSubmissionRepository.findAllByQuiz_Id(quizId)
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

            if (studentAnswer.getSelectedAnswer().equals(question.getCorrectAnswer())) {
                score += question.getPoints();
                numOfCorrect++;
            }
        }

        return new ScoreResult(score, numOfCorrect);
    }

    public static class ScoreResult {
        private int totalScore;
        private int numCorrect;

        public ScoreResult(int totalScore, int numCorrect) {
            this.totalScore = totalScore;
            this.numCorrect = numCorrect;
        }

        public int getTotalScore() { return totalScore; }
        public int getNumCorrect() { return numCorrect; }
    }

}


