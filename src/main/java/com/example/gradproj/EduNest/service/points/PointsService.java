package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.quizentity.QuizSubmission;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;

public interface PointsService {
    void awardQuizScorePoints(QuizSubmission submission);
    void awardTaskFinalScorePoints(TaskSubmission submission);

}
