package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("""
        SELECT COUNT(e.id)
        FROM Enrollment e
        WHERE e.mentorShip.mentor.id = :mentorId
    """)
    long countStudentsByMentorId(@Param("mentorId") Long mentorId);

    List<Enrollment> findByStudent(Student student);

    List<Enrollment> findByMentorShip(MentorShip mentorShip);

    Enrollment findByMentorShipAndStudent(MentorShip mentorShip, Student student);

    int countByMentorShip(MentorShip mentorShip);
    boolean existsByMentorShip_IdAndStudent_Id(Long mentorshipId, Long studentId);
}
