package com.example.gradproj.EduNest.service.quiz.submission;


import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.dto.quiz.response.StudentQuizReviewDTO;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.quiz.StudentAnswer;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final SecurityService securityService;



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

//        String penaltyMessage = "";

        String email = securityService.getCurrentUserEmail();
        if (!securityService.isStudentEnrolledByQuizId(email, quizId)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        Student student = studentRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Quiz is not available");
        }

//        if (quizSubmissionDTO.getAnswers() == null
//                || quizSubmissionDTO.getAnswers().isEmpty()) {
//            throw new globalLogicEx("Answers are required");
//        }

        QuizSubmission quizSubmission = quizSubmissionRepository
                .findByStudent_IdAndQuiz_Id(student.getId(), quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not started yet"));

        System.out.println(quizSubmission.getScore());
        if (quizSubmission.getScore() != null) {
            throw new globalLogicEx("Quiz already submitted");
        }

        Map<Long, Question> questions =
                quiz.getQuestions().stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));

        ScoreResult score = calculateScore(quizSubmissionDTO.getAnswers(), questions);


        double finalScore = score.getTotalScore();
        if (LocalDateTime.now().isAfter(quizSubmission.getEndDate())) {
            long minutesLate = ChronoUnit.MINUTES.between(
                    quizSubmission.getEndDate(),
                    LocalDateTime.now()
            );
            finalScore = Math.max(0, finalScore - minutesLate);
//            penaltyMessage = " (Late submission: -" + minutesLate + " points penalty)";
        }


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



        quizSubmission.getAnswers().clear();
        quizSubmission.getAnswers().addAll(studentAnswers);
        quizSubmission.setScore(finalScore);


        quizSubmissionRepository.save(quizSubmission);

        MentorShip mentorship = quizSubmission.getQuiz().getWeek().getMentorship();
        totalPointsService.applyDelta(quizSubmission.getStudent(), mentorship, (int) finalScore);

        String mentorEmail = quiz.getWeek().getMentorship().getMentor().getEmail();
        String studentName = quizSubmission.getStudent().getFirstName() + " " + quizSubmission.getStudent().getLastName();
        notificationService.sendToUserByEmail(
                mentorEmail,
                "New Quiz Submission",
                studentName + " submitted quiz " + quiz.getTitle() + " ",
                NotificationType.QUIZ
        );

        notificationService.sendToUserByEmail(
                student.getEmail(),
                "Quiz Submitted",
                "You scored " + finalScore + " in quiz " + quiz.getTitle() +
                        " in mentorship " + mentorship.getTitle() ,
                NotificationType.QUIZ
        );

        return QuizSubmissionResponseDTO.builder()
                .id(quizSubmission.getId())
                .studentId(student.getId())
                .studentName(student.getFirstName() + " " + student.getLastName())
                .score(finalScore)
                .totalPoints(quiz.getQuestions().stream().mapToInt(Question::getPoints).sum())
                .status("Submitted")
                .build();

    }

    @Override
    public List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId){
        String email = securityService.getCurrentUserEmail();
        boolean isMentorOwnQuiz = securityService.isMentorOwnQuiz(quizId, email);

        if (!isMentorOwnQuiz) {
            throw new AccessDeniedException("You are not authorized to view these answers");
        }

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

    @Override
    public List<StudentQuizReviewDTO> getStudentQuizReview(Long quizId){
        String email = securityService.getCurrentUserEmail();
        boolean isStudentEnrolled = securityService.isStudentEnrolledByQuizId(email, quizId);

        if (!isStudentEnrolled) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }

        Long studentId = securityService.getCurrentStudentId();

        QuizSubmission submission = quizSubmissionRepository
                .findByStudent_IdAndQuiz_Id(studentId, quizId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));

//        return submission.getAnswers().stream()
//                .map(ans -> StudentQuizReviewDTO.builder()
//                        .questionId(ans.getQuestion().getId())
//                        .text(ans.getQuestion().getText())
//                        .optionA(ans.getQuestion().getOptionA())
//                        .optionB(ans.getQuestion().getOptionB())
//                        .optionC(ans.getQuestion().getOptionC())
//                        .optionD(ans.getQuestion().getOptionD())
//                        .correctAnswer(String.valueOf(ans.getQuestion().getCorrectAnswer()))
//                        .selectedAnswer(ans == null ? null : ans.getSelectedAnswer())
//                        .build())
//                .toList();
        Quiz quiz = submission.getQuiz();

        return quiz.getQuestions().stream()
                .map(question -> {

                    StudentAnswer answer = submission.getAnswers().stream()
                            .filter(a -> a.getQuestion().getId().equals(question.getId()))
                            .findFirst()
                            .orElse(null);

                    return StudentQuizReviewDTO.builder()
                            .questionId(question.getId())
                            .text(question.getText())
                            .optionA(question.getOptionA())
                            .optionB(question.getOptionB())
                            .optionC(question.getOptionC())
                            .optionD(question.getOptionD())
                            .correctAnswer(String.valueOf(question.getCorrectAnswer()))
                            .selectedAnswer(
                                    answer == null ? null : answer.getSelectedAnswer()
                            )
                            .build();
                })
                .toList();
    }

    @Override
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId, int page, int size){
        Long mentorId = securityService.getCurrentMentorId();

        securityService.validateMentorHasAccessToStudent(studentId);

        return quizSubmissionRepository.findAllByStudentAndMentor(studentId, mentorId, PageRequest.of(page, size))
                .stream()
                .map(sub -> QuizSubmissionResponseDTO.builder()
                        .id(sub.getId())
                        .studentId(studentId)
                        .studentName(sub.getStudent().getFirstName() + " " + sub.getStudent().getLastName())
                        .score(sub.getScore())
                        .build())
                .toList();
    }

    @Override
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId, int page, int size){
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to view submissions for this quiz");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        double fullMark = quiz.getQuestions().stream()
                .map(Question::getPoints)
                .reduce(0, Integer::sum);

        return  quizSubmissionRepository.findAllByQuiz_Id(quizId, PageRequest.of(page, size))
                .stream()
                .map(sub -> {
                    String status;
                    if (sub.getScore() == null) {
                        status = "Not Submitted";
                    } else if (sub.getScore() >= (fullMark / 2)) {
                        status = "Passed";
                    } else {
                        status = "Failed";
                    }

                    return QuizSubmissionResponseDTO.builder()
                            .id(sub.getId())
                            .studentId(sub.getStudent().getId())
                            .studentName(sub.getStudent().getFirstName() + " " + sub.getStudent().getLastName())
                            .score(sub.getScore())
                            .status(status)
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


