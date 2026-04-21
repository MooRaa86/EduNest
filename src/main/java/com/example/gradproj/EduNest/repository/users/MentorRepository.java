package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto;
import com.example.gradproj.EduNest.entity.users.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);
    Optional<Mentor> findByEmail(String email);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto(
        m.profileImageUrl,
        m.firstName,
        m.lastName,
        m.yearsOfExperience,
        COUNT(DISTINCT e.id),
        COUNT(DISTINCT r.id),
        AVG(r.rating),
        m.bio,
        m.email,
        null
    )
    FROM Mentor m
    LEFT JOIN m.mentorships ms
    LEFT JOIN ms.enrollments e
    LEFT JOIN ms.reviews r
    WHERE m.email = :email
    GROUP BY m.id, m.profileImageUrl, m.firstName, m.lastName, m.bio, m.email
    """)
    MentorProfileforStudentDto findMentorProfileByEmail(@Param("email") String email);
}
