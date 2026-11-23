package com.example.gradproj.EduNest.service.quiz.submission;


import com.example.gradproj.EduNest.config.learning.LateSubmissionProperties;
import com.example.gradproj.EduNest.utils.PaginationPolicy;
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
import java.util.UUID;
import java.util.HashMap;
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
    private final LateSubmissionProperties lateSubmissionProperties;



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

        // EB-51 — L2 (quiz row) -> L3 (submission row) shared gate: with both
        // locks held there is no window in which a concurrent double submit
        // passes the score!=null eligibility check, and quiz deletion is
        // serialised against this award path.
        quizRepository.findForUpdateById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        QuizSubmission quizSubmission = quizSubmissionRepository
                .findByStudentIdAndQuizIdForUpdate(student.getId(), quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not started yet"));
        if (quizSubmission.getScore() != null) {
            throw new globalLogicEx("Quiz already submitted");
        }

        /*
         * EB-53 / D-08 — late policy: the attempt window may be exceeded by at
         * most the configured grace (app.submission.late-grace-minutes, default 10).
         * Beyond the grace the submission is rejected BEFORE any mutation —
         * answers, score, points and notifications are untouched. Within the
         * grace the submission is accepted, flagged isLate=true, with the
         * existing -1 pt/minute penalty (floored at 0).
         *
         * The grace boundary is compared on exact timestamps so that a
         * submission even one second past deadline + grace is rejected;
         * ChronoUnit.MINUTES is kept only for the per-minute penalty.
         */
        LocalDateTime now = LocalDateTime.now();
        boolean late = quizSubmission.getEndDate() != null && now.isAfter(quizSubmission.getEndDate());
        long minutesLate = late ? ChronoUnit.MINUTES.between(quizSubmission.getEndDate(), now) : 0;
        if (late && now.isAfter(
                quizSubmission.getEndDate().plusMinutes(lateSubmissionProperties.getLateGraceMinutes()))) {
            throw new globalLogicEx(
                    "Quiz submission window has closed (deadline was " + quizSubmission.getEndDate() + ")");
        }

        Map<UUID, Question> questions = new HashMap<>();
        for (Question q : quiz.getQuestions()) {
            if (q.getPublicId() != null) questions.put(q.getPublicId(), q);
        }

        ScoreResult score = calculateScore(quizSubmissionDTO.getAnswers(), questions);


        double finalScore = score.getTotalScore();
        if (late) {
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
        quizSubmission.setIsLate(late);


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
                .id(quizSubmission.getPublicId())
                .studentId(student.getPublicId())
                .studentName(student.getFirstName() + " " + student.getLastName())
                .score(finalScore)
                .totalPoints(quiz.getQuestions().stream().mapToInt(Question::getPoints).sum())
                .status("Submitted")
                .isLate(quizSubmission.getIsLate())
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
                        .questionId(ans.getQuestion().getPublicId())
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
//                        .questionId(ans.getQuestion().getPublicId())
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

                    /*
                     * P1-18 / LEARN-01 — answer-key scoping: the key is part of the review
                     * only once the attempt is genuinely submitted. Opening a quiz already
                     * creates the submission row (LEARN-07) with score == null, while
                     * submitQuizAnswers sets score — the same marker it uses as its
                     * "already submitted" guard. Pre-submission the field stays null and
                     * StudentQuizReviewDTO omits it from the JSON entirely.
                     */
                    return StudentQuizReviewDTO.builder()
                            .questionId(question.getPublicId())
                            .text(question.getText())
                            .optionA(question.getOptionA())
                            .optionB(question.getOptionB())
                            .optionC(question.getOptionC())
                            .optionD(question.getOptionD())
                            .correctAnswer(submission.getScore() != null
                                    ? String.valueOf(question.getCorrectAnswer())
                                    : null)
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

        return quizSubmissionRepository.findAllByStudentAndMentor(studentId, mentorId, PaginationPolicy.ofClamped(page, size))
                .stream()
                .map(sub -> QuizSubmissionResponseDTO.builder()
                        .id(sub.getPublicId())
                        .studentId(sub.getStudent().getPublicId())
                        .studentName(sub.getStudent().getFirstName() + " " + sub.getStudent().getLastName())
                        .score(sub.getScore())
                        .isLate(sub.getIsLate())
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

        // EB-60 / P4-11 — F3: fullMark was previously recomputed per submission
        // row inside the mapping loop; the questions collection is identical
        // for every row of this quiz, so compute it once. The pass threshold
        // variable stays double exactly like the original implementation
        // (fullMark / 2 with 5 total points is 2.5, not 2); totalPoints for the
        // DTO stays int.
        int totalPoints = quiz.getQuestions().stream()
                .mapToInt(Question::getPoints)
                .sum();
        double fullMark = totalPoints;

        return  quizSubmissionRepository.findAllByQuiz_Id(quizId, PaginationPolicy.ofClamped(page, size))
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
                            .id(sub.getPublicId())
                            .studentId(sub.getStudent().getPublicId())
                            .studentName(sub.getStudent().getFirstName() + " " + sub.getStudent().getLastName())
                            .totalPoints(totalPoints)
                            .score(sub.getScore())
                            .status(status)
                            .isLate(sub.getIsLate())
                            .build();
                })
                .toList();
    }


    private ScoreResult calculateScore(List<StudentAnswerDTO> answers, Map<?, Question> questions) {
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


    @Override
    @Transactional
    public QuizSubmissionResponseDTO submitQuizAnswers(QuizSubmissionDTO quizSubmissionDTO, UUID quizId) {
        Long internalId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return submitQuizAnswers(quizSubmissionDTO, internalId);
    }

    @Override
    public List<StudentAnswerDTO> getStudentAnswers(UUID studentId, UUID quizId) {
        Long internalStudentId = studentRepository.findIdByPublicId(studentId)
                .orElseThrow(() -> new globalLogicEx("Student not found"));
        Long internalQuizId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return getStudentAnswers(internalStudentId, internalQuizId);
    }

    @Override
    public List<StudentQuizReviewDTO> getStudentQuizReview(UUID quizId) {
        Long internalId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return getStudentQuizReview(internalId);
    }

    @Override
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(UUID quizId, int page, int size) {
        Long internalId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return getAllSubmissionsByQuiz(internalId, page, size);
    }

    @Override
    public List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(UUID studentId, int page, int size) {
        Long internalId = studentRepository.findIdByPublicId(studentId)
                .orElseThrow(() -> new globalLogicEx("Student not found"));
        return getAllSubmissionsByStudent(internalId, page, size);
    }
}


