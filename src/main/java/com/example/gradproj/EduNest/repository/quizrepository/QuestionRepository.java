package com.example.gradproj.EduNest.repository.quizrepository;

import com.example.gradproj.EduNest.entity.quizentity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuiz_Id(Long quizId);
}
