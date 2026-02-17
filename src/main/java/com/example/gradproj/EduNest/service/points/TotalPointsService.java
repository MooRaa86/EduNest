package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;

public interface TotalPointsService {
    int getTotalPoints(Long studentId, Long mentorshipId);
    void applyDelta(Student student, MentorShip mentorship, int delta);
}
