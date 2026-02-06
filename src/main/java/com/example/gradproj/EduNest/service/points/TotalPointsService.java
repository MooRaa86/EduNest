package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TotalPointsService {
    private final TotalPointsRepository totalPointsRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;

    @Transactional
    public void recalculate(Student student, mentorShipE mentorship) {

        int tasksTotal = taskSubmissionRepository.sumFinalScoresForMentorship(
                student.getId(), mentorship.getId());

        int quizzesTotal = quizSubmissionRepository.sumScoresForMentorship(
                student.getId(), mentorship.getId());

        int total = tasksTotal + quizzesTotal;

        TotalPoints tp = totalPointsRepository
                .findByStudent_IdAndMentorship_Id(student.getId(), mentorship.getId())
                .orElseGet(() -> TotalPoints.builder()
                        .student(student)
                        .mentorship(mentorship)
                        .totalPoints(0)
                        .build());

        tp.setTotalPoints(total);
        totalPointsRepository.save(tp);
    }

    @Transactional(readOnly = true)
    public int getTotalPoints(Long studentId, Long mentorshipId) {
        return totalPointsRepository
                .findByStudent_IdAndMentorship_Id(studentId, mentorshipId)
                .map(TotalPoints::getTotalPoints)
                .orElse(0);
    }
}
