package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface mentorShipRepository extends JpaRepository<mentorShipE, Long> {
    boolean existsById(Long id);
    long countByMentorId(Long mentorId);

    @Query("""
    SELECT COUNT(s.id)
    FROM mentorShipE ms
    JOIN ms.students s
    WHERE ms.mentor.id = :mentorId 
        """)
    long countStudentsByMentorId(@Param("mentorId") Long mentorId);


}
