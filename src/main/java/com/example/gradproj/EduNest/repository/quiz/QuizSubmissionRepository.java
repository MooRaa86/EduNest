package com.example.gradproj.EduNest.repository.quiz;

import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    boolean existsByStudent_IdAndQuiz_Id(Long studentId, Long quizId);

    Page<QuizSubmission> findAllByQuiz_Id(Long quizId, Pageable pageable);

    Page<QuizSubmission> findAllByStudent_Id(Long studentId, Pageable pageable);

    Optional<QuizSubmission> findByStudent_IdAndQuiz_Id(Long studentId, Long quizId);

}
