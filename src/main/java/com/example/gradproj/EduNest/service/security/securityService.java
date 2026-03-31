package com.example.gradproj.EduNest.service.security;

import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class securityService {
    private final EnrollmentRepository enrollmentRepository;
    private final MentorShipRepository mentorShipRepository;

    public boolean isMentorOwnerOfMentorship(String mentorEmail, Long mentorshipId) {
        return mentorShipRepository.getMentorshipStats(mentorshipId, mentorEmail) != null;
    }

    public boolean isStudentEnrolledByMentorshipId(String stEmail, Long mentorShipId) {
        return enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorShipId, stEmail);
    }

    public boolean isStudentEnrolledByWeekId(String studentEmail, Long weekId) {
        return enrollmentRepository.isStudentEnrolledInWeekMentorshipByEmail(weekId, studentEmail);
    }
}
