package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TotalPointsServiceImp implements TotalPointsService {
    private final TotalPointsRepository totalPointsRepository;

    @Transactional(readOnly = true)
    public int getTotalPoints(Long studentId, Long mentorshipId) {
        return totalPointsRepository
                .findByStudent_IdAndMentorship_Id(studentId, mentorshipId)
                .map(TotalPoints::getTotalPoints)
                .orElse(0);
    }
    @Transactional
    @Override
    public void applyDelta(Student student, MentorShip mentorship, int delta) {
        if (delta == 0) return;

        TotalPoints tp = totalPointsRepository
                .findForUpdate(student.getId(), mentorship.getId())
                .orElseGet(() -> TotalPoints.builder()
                        .student(student)
                        .mentorship(mentorship)
                        .totalPoints(0)
                        .build());

        tp.setTotalPoints(tp.getTotalPoints() + delta);


        if (tp.getTotalPoints() < 0) tp.setTotalPoints(0);

        totalPointsRepository.save(tp);
    }
}
