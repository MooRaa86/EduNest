package com.example.gradproj.EduNest.service.quiz.submission;


import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.quiz.StudentAnswer;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class QuizSubmissionServiceImpl implements QuizSubmissionService {

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final StudentRepository studentRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final NotificationService notificationService;



    private String getCurrentStudentEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }

    @Override
    @Transactional
    public QuizSubmissionResponseDTO submitQuizAnswers(
            QuizSubmissionDTO quizSubmissionDTO, Long quizId) {

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        Student student = studentRepository
                .findByEmail(getCurrentStudentEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

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
                .build();

        List<StudentAnswer> studentAnswers =
                quizSubmissionDTO.getAnswers().stream()
                        .<StudentAnswer>map(dto -> {
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

        QuizSubmission saved = quizSubmissionRepository.save(quizSubmission);

        MentorShip mentorship = saved.getQuiz().getWeek().getMentorship();
        totalPointsService.applyDelta(saved.getStudent(), mentorship,saved.getScore().intValue() );


        String mentorEmail = quiz.getWeek().getMentorship().getMentor().getEmail();
        String studentName = saved.getStudent().getFirstName() + " " + saved.getStudent().getLastName();
        notificationService.sendToUserByEmail(
                mentorEmail,
                "New Quiz Submission",
                studentName + " submitted quiz " + quiz.getTitle() + " ",
                NotificationType.QUIZ
        );

        notificationService.sendToUserByEmail(
                student.getEmail(),
                "Quiz Submitted",
                "You scored " + saved.getScore() + " in quiz " + quiz.getTitle() + " in mentorship " + mentorship.getTitle() ,
                NotificationType.QUIZ
        );

        return QuizSubmissionResponseDTO.builder()
                .id(saved.getId())
                .studentId(student.getId())
                .score(saved.getScore())
                .build();

    }

    public List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId){
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

  public  List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId, int page, int size){
        return quizSubmissionRepository.findAllByStudent_Id(studentId , PageRequest.of(page, size))
                .stream()
                .map(sub -> QuizSubmissionResponseDTO.builder()
                        .id(sub.getId())
                        .studentId(studentId)
                        .score(sub.getScore())
                        .build())
                .toList();
    }

    @Override
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId, int page, int size){
        if(!quizRepository.existsById(quizId)) {
            throw new globalLogicEx("Quiz not found");
        }

        return  quizSubmissionRepository.findAllByQuiz_Id(quizId, PageRequest.of(page, size))
                .stream()
                .map(sub -> {

                    return QuizSubmissionResponseDTO.builder()
                            .id(sub.getId())
                            .studentId(sub.getStudent().getId())
                            .studentName(sub.getStudent().getFirstName() + " " + sub.getStudent().getLastName())
                            .score(sub.getScore())
                            .build();
                })
                .toList();
    }


    private ScoreResult calculateScore(List<StudentAnswerDTO> answers, Map<Long, Question> questions) {
        double score = 0;
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
        private double totalScore;
        private int numCorrect;

        public ScoreResult(double totalScore, int numCorrect) {
            this.totalScore = totalScore;
            this.numCorrect = numCorrect;
        }

    }

}


