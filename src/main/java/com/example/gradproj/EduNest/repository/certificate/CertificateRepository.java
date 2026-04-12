package com.example.gradproj.EduNest.repository.certificate;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    @Query("SELECT c.student.id FROM Certificate c WHERE c.mentorship.id = :mentorshipId")
    Set<Long> findStudentIdsByMentorshipId(@Param("mentorshipId") Long mentorshipId);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.certificate.CertificateResponse(
        CONCAT(c.student.firstName, ' ', c.student.lastName),
        CONCAT(c.mentorship.mentor.firstName, ' ', c.mentorship.mentor.lastName),
        c.mentorship.title,
        c.mentorship.subtitle,
        c.issuedAt,
        c.rank
    )
    FROM Certificate c
    WHERE c.student.email = :email
    ORDER BY c.issuedAt DESC
    """)
    List<CertificateResponse> findByStudentEmail(@Param("email") String email);
}
