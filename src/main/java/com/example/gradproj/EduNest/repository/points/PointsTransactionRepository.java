package com.example.gradproj.EduNest.repository.points;

import com.example.gradproj.EduNest.entity.points.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Integer> {
    Optional<PointsTransaction> findByTaskSubmission_Id(Long taskSubmissionId);
    Optional<PointsTransaction> findByQuizSubmission_Id(Long quizSubmissionId);


}
