package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.points.PointsTransaction;
import com.example.gradproj.EduNest.entity.quizentity.QuizSubmission;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.points.PointsReason;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.points.PointsTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PointsServiceImp implements  PointsService {
    private final PointsTransactionRepository pointsRepository;

    @Override
    @Transactional
    public void awardQuizScorePoints(QuizSubmission submission) {
        if (submission == null || submission.getId() == null) return;

        Integer newScore = submission.getScore();
        if (newScore == null || newScore < 0) return;

        Student student = submission.getStudent();
        if (student == null) return;

        PointsTransaction tx = pointsRepository.findByQuizSubmission_Id(submission.getId()).orElse(null);

        int currentTotal = java.util.Objects.requireNonNullElse(student.getTotalPoints(), 0);

        if (tx == null) {
            PointsTransaction created = PointsTransaction.builder()
                    .student(student)
                    .points(newScore)
                    .reason(PointsReason.QUIZ_SCORE)
                    .note("Quiz submitted: " + submission.getQuiz().getTitle())
                    .quizSubmission(submission)
                    .build();

            pointsRepository.save(created);
            student.setTotalPoints(currentTotal + newScore);
        } else {
            int oldScore = java.util.Objects.requireNonNullElse(tx.getPoints(), 0);
            int delta = newScore - oldScore;
            if (delta == 0) return;

            tx.setPoints(newScore);
            tx.setNote("Quiz score updated: " + submission.getQuiz().getTitle());
            student.setTotalPoints(currentTotal + delta);
        }
    }

    @Override
    @Transactional
    public void awardTaskFinalScorePoints(TaskSubmission submission) {
        if (submission == null || submission.getId() == null) return;
        if (submission.getStatus() != SubmissionStatus.GRADED) return;

        Integer newScore = submission.getFinalScore();
        if (newScore == null || newScore < 0) return;

        Student student = submission.getStudent();
        if (student == null) return;

        PointsTransaction pointsTransaction = pointsRepository.findByTaskSubmission_Id(submission.getId())
                .orElse(null);

        int currentTotal = java.util.Objects.requireNonNullElse(student.getTotalPoints(), 0);

        if (pointsTransaction == null) {
            PointsTransaction created = PointsTransaction.builder()
                    .student(student)
                    .points(newScore)
                    .reason(PointsReason.TASK_SCORE)
                    .note("Task graded: " + submission.getTask().getTitle())
                    .taskSubmission(submission)
                    .build();

            pointsRepository.save(created);
            student.setTotalPoints(currentTotal + newScore);

        } else {
            int oldScore = java.util.Objects.requireNonNullElse(pointsTransaction.getPoints(), 0);
            int delta = newScore - oldScore;


            if (delta == 0) return;

            pointsTransaction.setPoints(newScore);
            pointsTransaction.setNote("Task re-graded: " + submission.getTask().getTitle());
            student.setTotalPoints(currentTotal + delta);
        }

    }
}
